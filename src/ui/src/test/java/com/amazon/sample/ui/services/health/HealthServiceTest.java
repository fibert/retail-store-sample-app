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

import static org.assertj.core.api.Assertions.assertThat;

import com.amazon.sample.ui.config.EndpointProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class HealthServiceTest {

  @Test
  void reportsUpWhenAllDependenciesRespond() {
    var recorder = new RequestRecorder(req -> response(HttpStatus.OK));
    var service = new HealthService(buildClient(recorder), allEndpoints());

    StepVerifier.create(service.checkHealth())
      .assertNext(summary -> {
        assertThat(summary.getStatus()).isEqualTo(HealthStatus.UP);
        assertThat(summary.isHealthy()).isTrue();
        assertThat(summary.getDependencies())
          .containsEntry("catalog", HealthStatus.UP)
          .containsEntry("carts", HealthStatus.UP)
          .containsEntry("orders", HealthStatus.UP)
          .containsEntry("checkout", HealthStatus.UP);
      })
      .verifyComplete();
  }

  @Test
  void fallsBackToActuatorHealthWhenHealthIsMissing() {
    var recorder = new RequestRecorder(req ->
      req.url().getPath().equals("/actuator/health")
        ? response(HttpStatus.OK)
        : response(HttpStatus.NOT_FOUND)
    );
    var service = new HealthService(buildClient(recorder), allEndpoints());

    StepVerifier.create(service.checkHealth())
      .assertNext(summary ->
        assertThat(summary.getStatus()).isEqualTo(HealthStatus.UP)
      )
      .verifyComplete();
  }

  @Test
  void reportsDownWhenAnyDependencyIsUnreachable() {
    var recorder = new RequestRecorder(req ->
      req.url().getHost().equals("orders")
        ? response(HttpStatus.INTERNAL_SERVER_ERROR)
        : response(HttpStatus.OK)
    );
    var service = new HealthService(buildClient(recorder), allEndpoints());

    StepVerifier.create(service.checkHealth())
      .assertNext(summary -> {
        assertThat(summary.getStatus()).isEqualTo(HealthStatus.DOWN);
        assertThat(summary.isHealthy()).isFalse();
        assertThat(summary.getDependencies())
          .containsEntry("orders", HealthStatus.DOWN)
          .containsEntry("catalog", HealthStatus.UP);
      })
      .verifyComplete();
  }

  @Test
  void reportsUnknownWhenEndpointNotConfigured() {
    var recorder = new RequestRecorder(req -> response(HttpStatus.OK));
    var endpoints = new EndpointProperties();
    endpoints.setCatalog("http://catalog");
    var service = new HealthService(buildClient(recorder), endpoints);

    StepVerifier.create(service.checkHealth())
      .assertNext(summary -> {
        assertThat(summary.getStatus()).isEqualTo(HealthStatus.UP);
        assertThat(summary.getDependencies())
          .containsEntry("catalog", HealthStatus.UP)
          .containsEntry("carts", HealthStatus.UNKNOWN)
          .containsEntry("orders", HealthStatus.UNKNOWN)
          .containsEntry("checkout", HealthStatus.UNKNOWN);
      })
      .verifyComplete();

    assertThat(recorder.requestedHosts).containsOnly("catalog");
  }

  private static EndpointProperties allEndpoints() {
    var endpoints = new EndpointProperties();
    endpoints.setCatalog("http://catalog");
    endpoints.setCarts("http://carts");
    endpoints.setOrders("http://orders");
    endpoints.setCheckout("http://checkout");
    return endpoints;
  }

  private WebClient buildClient(ExchangeFunction exchange) {
    return WebClient.builder().exchangeFunction(exchange).build();
  }

  private static Mono<ClientResponse> response(HttpStatus status) {
    return Mono.just(ClientResponse.create(status).body("").build());
  }

  private static class RequestRecorder implements ExchangeFunction {

    final List<String> requestedHosts = new ArrayList<>();
    private final Function<ClientRequest, Mono<ClientResponse>> handler;

    RequestRecorder(Function<ClientRequest, Mono<ClientResponse>> handler) {
      this.handler = handler;
    }

    @Override
    public Mono<ClientResponse> exchange(ClientRequest request) {
      requestedHosts.add(request.url().getHost());
      return handler.apply(request);
    }
  }
}
