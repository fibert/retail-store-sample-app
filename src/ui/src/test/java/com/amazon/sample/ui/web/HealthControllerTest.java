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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazon.sample.ui.config.EndpointProperties;
import com.amazon.sample.ui.web.util.TopologyService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class HealthControllerTest {

  private static final String CATALOG = "http://catalog:8080";
  private static final String CARTS = "http://carts:8080";
  private static final String CHECKOUT = "http://checkout:8080";
  private static final String ORDERS = "http://orders:8080";

  private EndpointProperties endpoints;
  private TopologyService topologyService;
  private HealthController controller;

  @BeforeEach
  void setUp() {
    endpoints = new EndpointProperties();
    endpoints.setCatalog(CATALOG);
    endpoints.setCarts(CARTS);
    endpoints.setCheckout(CHECKOUT);
    endpoints.setOrders(ORDERS);

    topologyService = mock(TopologyService.class);
    controller = new HealthController(endpoints, topologyService);
  }

  @Test
  @SuppressWarnings("unchecked")
  void returnsOkWhenAllBackendsHealthy() {
    when(topologyService.checkHealth(eq(CATALOG))).thenReturn(Mono.just(true));
    when(topologyService.checkHealth(eq(CARTS))).thenReturn(Mono.just(true));
    when(topologyService.checkHealth(eq(CHECKOUT))).thenReturn(Mono.just(true));
    when(topologyService.checkHealth(eq(ORDERS))).thenReturn(Mono.just(true));

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
        var components = (Map<String, Object>) response
          .getBody()
          .get("components");
        assertThat(components)
          .containsEntry("catalog", "UP")
          .containsEntry("carts", "UP")
          .containsEntry("checkout", "UP")
          .containsEntry("orders", "UP");
      })
      .verifyComplete();
  }

  @Test
  @SuppressWarnings("unchecked")
  void returnsServiceUnavailableWhenAnyBackendUnhealthy() {
    when(topologyService.checkHealth(eq(CATALOG))).thenReturn(Mono.just(true));
    when(topologyService.checkHealth(eq(CARTS))).thenReturn(Mono.just(false));
    when(topologyService.checkHealth(eq(CHECKOUT))).thenReturn(Mono.just(true));
    when(topologyService.checkHealth(eq(ORDERS))).thenReturn(Mono.just(true));

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(
          HttpStatus.SERVICE_UNAVAILABLE
        );
        assertThat(response.getBody()).containsEntry("status", "DOWN");
        var components = (Map<String, Object>) response
          .getBody()
          .get("components");
        assertThat(components).containsEntry("carts", "DOWN");
      })
      .verifyComplete();
  }

  @Test
  void returnsOkWhenNoBackendsConfigured() {
    endpoints.setCatalog(null);
    endpoints.setCarts(null);
    endpoints.setCheckout(null);
    endpoints.setOrders(null);

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
      })
      .verifyComplete();
  }
}
