/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: MIT-0
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.amazon.sample.ui.services.health;

import com.amazon.sample.ui.config.EndpointProperties;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Performs a deep health check of the UI's downstream dependencies by probing
 * each service's health endpoint.
 */
@Service
@Slf4j
public class HealthService {

  private static final int CONNECT_TIMEOUT = 1000;
  private static final int RESPONSE_TIMEOUT = 1000;

  private final WebClient webClient;
  private final EndpointProperties endpoints;

  @Autowired
  public HealthService(
    WebClient.Builder webClientBuilder,
    EndpointProperties endpoints
  ) {
    HttpClient httpClient = HttpClient.create()
      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT)
      .responseTimeout(Duration.ofMillis(RESPONSE_TIMEOUT));

    this.webClient = webClientBuilder
      .clientConnector(new ReactorClientHttpConnector(httpClient))
      .build();
    this.endpoints = endpoints;
  }

  HealthService(WebClient webClient, EndpointProperties endpoints) {
    this.webClient = webClient;
    this.endpoints = endpoints;
  }

  public Mono<HealthSummary> checkHealth() {
    return Flux.merge(
      checkDependency("catalog", endpoints.getCatalog()),
      checkDependency("carts", endpoints.getCarts()),
      checkDependency("orders", endpoints.getOrders()),
      checkDependency("checkout", endpoints.getCheckout())
    )
      .collectList()
      .map(HealthSummary::new);
  }

  private Mono<DependencyHealth> checkDependency(String name, String endpoint) {
    if (endpoint == null || endpoint.isEmpty()) {
      return Mono.just(new DependencyHealth(name, HealthStatus.UNKNOWN));
    }

    return checkHealth(endpoint).map(healthy -> {
      if (!healthy) {
        log.warn("Health check failed for service {} at {}", name, endpoint);
      }
      return new DependencyHealth(
        name,
        healthy ? HealthStatus.UP : HealthStatus.DOWN
      );
    });
  }

  private Mono<Boolean> checkHealth(String endpoint) {
    return probeHealth(joinPath(endpoint, "health")).flatMap(ok ->
      ok ? Mono.just(true) : probeHealth(joinPath(endpoint, "actuator/health"))
    );
  }

  private Mono<Boolean> probeHealth(String url) {
    return webClient
      .get()
      .uri(url)
      .retrieve()
      .toBodilessEntity()
      .map(response -> response.getStatusCode().is2xxSuccessful())
      .onErrorReturn(false);
  }

  private String joinPath(String endpoint, String path) {
    return endpoint.endsWith("/") ? endpoint + path : endpoint + "/" + path;
  }
}
