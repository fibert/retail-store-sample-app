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
import static org.mockito.ArgumentMatchers.anyString;
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
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class HealthControllerTest {

  private TopologyService topologyService;
  private HealthController controller;

  @BeforeEach
  void setUp() {
    topologyService = mock(TopologyService.class);

    var endpoints = new EndpointProperties();
    endpoints.setCatalog("http://catalog");
    endpoints.setCarts("http://carts");
    endpoints.setCheckout("http://checkout");
    endpoints.setOrders("http://orders");
    endpoints.setRecommendations("http://recommendations");

    controller = new HealthController();
    ReflectionTestUtils.setField(controller, "endpoints", endpoints);
    ReflectionTestUtils.setField(
      controller,
      "topologyService",
      topologyService
    );
  }

  @Test
  void returns200WhenAllComponentsHealthy() {
    stubAll(TopologyStatus.HEALTHY);

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(response)).containsEntry("status", "UP");
      })
      .verifyComplete();
  }

  @Test
  void returns200WhenComponentIsNotConfigured() {
    stubAll(TopologyStatus.NONE);

    StepVerifier.create(controller.health())
      .assertNext(response ->
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK)
      )
      .verifyComplete();
  }

  @Test
  void returns503WhenAnyComponentUnhealthy() {
    stubAll(TopologyStatus.HEALTHY);
    when(
      topologyService.getTopologyForService(eq("orders"), anyString())
    ).thenReturn(topology("orders", TopologyStatus.UNHEALTHY));

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(
          HttpStatus.SERVICE_UNAVAILABLE
        );
        Map<String, Object> body = body(response);
        assertThat(body).containsEntry("status", "DOWN");
        assertThat(components(body)).containsEntry("orders", "UNHEALTHY");
      })
      .verifyComplete();
  }

  private void stubAll(TopologyStatus status) {
    for (String service : new String[] {
      "catalog",
      "carts",
      "checkout",
      "orders",
      "recommendations",
    }) {
      when(
        topologyService.getTopologyForService(eq(service), anyString())
      ).thenReturn(topology(service, status));
    }
  }

  private static Mono<TopologyInformation> topology(
    String service,
    TopologyStatus status
  ) {
    var info = new TopologyInformation();
    info.setServiceName(service);
    info.setStatus(status);
    return Mono.just(info);
  }

  private static Map<String, Object> body(
    ResponseEntity<Map<String, Object>> response
  ) {
    return response.getBody() == null ? new HashMap<>() : response.getBody();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> components(Map<String, Object> body) {
    return (Map<String, Object>) body.get("components");
  }
}
