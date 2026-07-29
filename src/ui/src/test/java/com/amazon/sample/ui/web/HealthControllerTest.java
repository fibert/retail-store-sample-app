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

  private TopologyService topologyService;
  private EndpointProperties endpoints;
  private HealthController controller;

  @BeforeEach
  void setUp() {
    topologyService = mock(TopologyService.class);
    endpoints = new EndpointProperties();
    endpoints.setCatalog("http://catalog");
    endpoints.setCarts("http://carts");
    endpoints.setOrders("http://orders");
    endpoints.setCheckout("http://checkout");
    controller = new HealthController(endpoints, topologyService);
  }

  private void stub(String name, TopologyStatus status) {
    var info = new TopologyInformation();
    info.setServiceName(name);
    info.setStatus(status);
    when(
      topologyService.getTopologyForService(name, "http://" + name)
    ).thenReturn(Mono.just(info));
  }

  @Test
  @SuppressWarnings("unchecked")
  void returns200AndUpWhenAllDependenciesHealthy() {
    stub("catalog", TopologyStatus.HEALTHY);
    stub("carts", TopologyStatus.HEALTHY);
    stub("orders", TopologyStatus.HEALTHY);
    stub("checkout", TopologyStatus.HEALTHY);

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
        var dependencies = (Map<String, String>) response
          .getBody()
          .get("dependencies");
        assertThat(dependencies)
          .containsEntry("catalog", "UP")
          .containsEntry("carts", "UP")
          .containsEntry("orders", "UP")
          .containsEntry("checkout", "UP");
      })
      .verifyComplete();
  }

  @Test
  @SuppressWarnings("unchecked")
  void returns503AndDownWhenAnyDependencyDown() {
    stub("catalog", TopologyStatus.HEALTHY);
    stub("carts", TopologyStatus.HEALTHY);
    stub("orders", TopologyStatus.UNHEALTHY);
    stub("checkout", TopologyStatus.HEALTHY);

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(
          HttpStatus.SERVICE_UNAVAILABLE
        );
        assertThat(response.getBody()).containsEntry("status", "DOWN");
        var dependencies = (Map<String, String>) response
          .getBody()
          .get("dependencies");
        assertThat(dependencies).containsEntry("orders", "DOWN");
      })
      .verifyComplete();
  }

  @Test
  @SuppressWarnings("unchecked")
  void reportsUnknownWithoutFailingWhenDependencyNotConfigured() {
    stub("catalog", TopologyStatus.HEALTHY);
    stub("carts", TopologyStatus.HEALTHY);
    stub("checkout", TopologyStatus.HEALTHY);
    var info = new TopologyInformation();
    info.setServiceName("orders");
    info.setStatus(TopologyStatus.NONE);
    when(
      topologyService.getTopologyForService("orders", "http://orders")
    ).thenReturn(Mono.just(info));

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var dependencies = (Map<String, String>) response
          .getBody()
          .get("dependencies");
        assertThat(dependencies).containsEntry("orders", "UNKNOWN");
      })
      .verifyComplete();
  }
}
