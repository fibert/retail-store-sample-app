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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazon.sample.ui.config.EndpointProperties;
import com.amazon.sample.ui.web.util.TopologyService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class HealthControllerTest {

  private final TopologyService topologyService = mock(TopologyService.class);

  private WebTestClient client(EndpointProperties endpoints) {
    return WebTestClient.bindToController(
      new HealthController(endpoints, topologyService)
    ).build();
  }

  private EndpointProperties allEndpoints() {
    var endpoints = new EndpointProperties();
    endpoints.setCatalog("http://catalog");
    endpoints.setCarts("http://carts");
    endpoints.setCheckout("http://checkout");
    endpoints.setOrders("http://orders");
    endpoints.setRecommendations("http://recommendations");
    return endpoints;
  }

  @Test
  void returnsOkWhenAllBackendsHealthy() {
    when(topologyService.checkHealth(eq("http://catalog"))).thenReturn(
      Mono.just(true)
    );
    when(topologyService.checkHealth(eq("http://carts"))).thenReturn(
      Mono.just(true)
    );
    when(topologyService.checkHealth(eq("http://checkout"))).thenReturn(
      Mono.just(true)
    );
    when(topologyService.checkHealth(eq("http://orders"))).thenReturn(
      Mono.just(true)
    );
    when(topologyService.checkHealth(eq("http://recommendations"))).thenReturn(
      Mono.just(true)
    );

    client(allEndpoints())
      .get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk()
      .expectBody()
      .jsonPath("$.status")
      .isEqualTo("UP");
  }

  @Test
  void returnsServiceUnavailableWhenAnyBackendUnhealthy() {
    when(topologyService.checkHealth(eq("http://catalog"))).thenReturn(
      Mono.just(true)
    );
    when(topologyService.checkHealth(eq("http://carts"))).thenReturn(
      Mono.just(false)
    );
    when(topologyService.checkHealth(eq("http://checkout"))).thenReturn(
      Mono.just(true)
    );
    when(topologyService.checkHealth(eq("http://orders"))).thenReturn(
      Mono.just(true)
    );
    when(topologyService.checkHealth(eq("http://recommendations"))).thenReturn(
      Mono.just(true)
    );

    client(allEndpoints())
      .get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isEqualTo(503)
      .expectBody()
      .jsonPath("$.status")
      .isEqualTo("DOWN")
      .jsonPath("$.services.carts")
      .isEqualTo(false);
  }

  @Test
  void treatsUnconfiguredEndpointsAsHealthy() {
    // No endpoints configured (mock mode) — nothing to probe, so healthy.
    client(new EndpointProperties())
      .get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk()
      .expectBody()
      .jsonPath("$.status")
      .isEqualTo("UP");
  }
}
