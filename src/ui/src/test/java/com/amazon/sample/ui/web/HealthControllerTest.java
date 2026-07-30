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
import com.amazon.sample.ui.web.util.TopologyInformation;
import com.amazon.sample.ui.web.util.TopologyService;
import com.amazon.sample.ui.web.util.TopologyStatus;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class HealthControllerTest {

  private EndpointProperties endpoints;
  private TopologyService topologyService;
  private HealthController controller;

  @BeforeEach
  void setUp() {
    endpoints = new EndpointProperties();
    endpoints.setCatalog("http://catalog");
    endpoints.setCarts("http://carts");
    endpoints.setOrders("http://orders");
    endpoints.setCheckout("http://checkout");

    topologyService = mock(TopologyService.class);
    controller = new HealthController(endpoints, topologyService);
  }

  private void stub(
    String serviceName,
    String endpoint,
    TopologyStatus status
  ) {
    var info = new TopologyInformation();
    info.setServiceName(serviceName);
    info.setEndpoint(endpoint);
    info.setStatus(status);
    when(
      topologyService.getTopologyForService(eq(serviceName), eq(endpoint))
    ).thenReturn(Mono.just(info));
  }

  @Test
  void returns200AndUpWhenAllDependenciesHealthy() {
    stub("catalog", "http://catalog", TopologyStatus.HEALTHY);
    stub("cart", "http://carts", TopologyStatus.HEALTHY);
    stub("orders", "http://orders", TopologyStatus.HEALTHY);
    stub("checkout", "http://checkout", TopologyStatus.HEALTHY);

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(response).get("status")).isEqualTo("UP");
        assertThat(dependencies(response))
          .containsEntry("catalog", "UP")
          .containsEntry("cart", "UP")
          .containsEntry("orders", "UP")
          .containsEntry("checkout", "UP");
      })
      .verifyComplete();
  }

  @Test
  void returns503AndDownWhenAnyDependencyUnhealthy() {
    stub("catalog", "http://catalog", TopologyStatus.HEALTHY);
    stub("cart", "http://carts", TopologyStatus.HEALTHY);
    stub("orders", "http://orders", TopologyStatus.UNHEALTHY);
    stub("checkout", "http://checkout", TopologyStatus.HEALTHY);

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(
          HttpStatus.SERVICE_UNAVAILABLE
        );
        assertThat(body(response).get("status")).isEqualTo("DOWN");
        assertThat(dependencies(response))
          .containsEntry("catalog", "UP")
          .containsEntry("orders", "DOWN");
      })
      .verifyComplete();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> body(ResponseEntity<Map<String, Object>> resp) {
    return resp.getBody() == null ? new HashMap<>() : resp.getBody();
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> dependencies(
    ResponseEntity<Map<String, Object>> resp
  ) {
    return (Map<String, String>) body(resp).get("dependencies");
  }
}
