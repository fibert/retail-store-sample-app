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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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
    endpoints.setCheckout("http://checkout");
    endpoints.setOrders("http://orders");
    endpoints.setRecommendations("http://recommendations");

    topologyService = mock(TopologyService.class);
    controller = new HealthController(endpoints, topologyService);
  }

  private void stub(String service, TopologyStatus status) {
    var info = new TopologyInformation();
    info.setServiceName(service);
    info.setStatus(status);
    when(
      topologyService.getTopologyForService(eq(service), anyString())
    ).thenReturn(Mono.just(info));
  }

  @Test
  void returns200WhenAllBackendsHealthy() {
    stub("catalog", TopologyStatus.HEALTHY);
    stub("carts", TopologyStatus.HEALTHY);
    stub("checkout", TopologyStatus.HEALTHY);
    stub("orders", TopologyStatus.HEALTHY);
    stub("recommendations", TopologyStatus.HEALTHY);

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
      })
      .verifyComplete();
  }

  @Test
  void returns503WhenAnyBackendUnhealthy() {
    stub("catalog", TopologyStatus.HEALTHY);
    stub("carts", TopologyStatus.UNHEALTHY);
    stub("checkout", TopologyStatus.HEALTHY);
    stub("orders", TopologyStatus.HEALTHY);
    stub("recommendations", TopologyStatus.HEALTHY);

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(
          HttpStatus.SERVICE_UNAVAILABLE
        );
        assertThat(response.getBody()).containsEntry("status", "DOWN");

        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) response
          .getBody()
          .get("components");
        assertThat(components).containsEntry("carts", "UNHEALTHY");
      })
      .verifyComplete();
  }

  @Test
  void treatsUnconfiguredBackendsAsHealthy() {
    // NONE means the backend is not wired to this UI; it must not fail health.
    stub("catalog", TopologyStatus.HEALTHY);
    stub("carts", TopologyStatus.NONE);
    stub("checkout", TopologyStatus.NONE);
    stub("orders", TopologyStatus.NONE);
    stub("recommendations", TopologyStatus.NONE);

    StepVerifier.create(controller.health())
      .assertNext(response ->
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK)
      )
      .verifyComplete();
  }
}
