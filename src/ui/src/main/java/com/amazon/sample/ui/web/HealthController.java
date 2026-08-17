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

package com.amazon.sample.ui.web;

import com.amazon.sample.ui.config.EndpointProperties;
import com.amazon.sample.ui.web.util.TopologyService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Deep health check for the UI component. Pings the configured backend
 * components and returns 200 only if all of them are healthy, otherwise 503.
 */
@RestController
@RequestMapping("/health")
@Slf4j
public class HealthController {

  private final EndpointProperties endpoints;

  private final TopologyService topologyService;

  public HealthController(
    EndpointProperties endpoints,
    TopologyService topologyService
  ) {
    this.endpoints = endpoints;
    this.topologyService = topologyService;
  }

  @GetMapping
  public Mono<ResponseEntity<Map<String, Object>>> health() {
    return Flux.merge(
      checkService("catalog", endpoints.getCatalog()),
      checkService("carts", endpoints.getCarts()),
      checkService("checkout", endpoints.getCheckout()),
      checkService("orders", endpoints.getOrders()),
      checkService("recommendations", endpoints.getRecommendations())
    )
      .collectMap(ServiceHealth::name, ServiceHealth::healthy)
      .map(services -> {
        boolean allHealthy = services
          .values()
          .stream()
          .allMatch(h -> h);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", allHealthy ? "UP" : "DOWN");
        body.put("services", services);

        HttpStatus status = allHealthy
          ? HttpStatus.OK
          : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(body);
      });
  }

  private Mono<ServiceHealth> checkService(String name, String endpoint) {
    if (endpoint == null || endpoint.isEmpty()) {
      // No backend configured (e.g. mock mode) — treat as healthy.
      return Mono.just(new ServiceHealth(name, true));
    }

    return topologyService
      .checkHealth(endpoint)
      .map(healthy -> {
        if (!healthy) {
          log.warn(
            "Deep health check failed for service {} at {}",
            name,
            endpoint
          );
        }
        return new ServiceHealth(name, healthy);
      });
  }

  private record ServiceHealth(String name, boolean healthy) {}
}
