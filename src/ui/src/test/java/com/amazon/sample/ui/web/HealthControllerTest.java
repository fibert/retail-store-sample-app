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
import static org.mockito.Mockito.when;

import com.amazon.sample.ui.config.EndpointProperties;
import com.amazon.sample.ui.web.util.TopologyInformation;
import com.amazon.sample.ui.web.util.TopologyService;
import com.amazon.sample.ui.web.util.TopologyStatus;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class HealthControllerTest {

  private TopologyService topologyService;
  private EndpointProperties endpoints;
  private HealthController controller;

  @BeforeEach
  void setUp() throws Exception {
    topologyService = Mockito.mock(TopologyService.class);
    endpoints = new EndpointProperties();
    endpoints.setCatalog("http://catalog");
    endpoints.setCarts("http://carts");
    endpoints.setCheckout("http://checkout");
    endpoints.setOrders("http://orders");

    controller = new HealthController();
    inject(controller, "topologyService", topologyService);
    inject(controller, "endpoints", endpoints);
  }

  @Test
  void returnsOkWhenAllComponentsHealthy() {
    stub("catalog", "http://catalog", TopologyStatus.HEALTHY);
    stub("carts", "http://carts", TopologyStatus.HEALTHY);
    stub("checkout", "http://checkout", TopologyStatus.HEALTHY);
    stub("orders", "http://orders", TopologyStatus.HEALTHY);

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
      })
      .verifyComplete();
  }

  @Test
  void returnsServiceUnavailableWhenAComponentIsUnhealthy() {
    stub("catalog", "http://catalog", TopologyStatus.HEALTHY);
    stub("carts", "http://carts", TopologyStatus.UNHEALTHY);
    stub("checkout", "http://checkout", TopologyStatus.HEALTHY);
    stub("orders", "http://orders", TopologyStatus.HEALTHY);

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(
          HttpStatus.SERVICE_UNAVAILABLE
        );
        assertThat(response.getBody()).containsEntry("status", "DOWN");
      })
      .verifyComplete();
  }

  @SuppressWarnings("unchecked")
  @Test
  void reportsPerComponentStatuses() {
    stub("catalog", "http://catalog", TopologyStatus.HEALTHY);
    stub("carts", "http://carts", TopologyStatus.UNHEALTHY);
    stub("checkout", "http://checkout", TopologyStatus.HEALTHY);
    stub("orders", "http://orders", TopologyStatus.HEALTHY);

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        Map<String, Object> components = (Map<String, Object>) response
          .getBody()
          .get("components");
        assertThat(components)
          .containsEntry("catalog", "HEALTHY")
          .containsEntry("carts", "UNHEALTHY")
          .containsEntry("checkout", "HEALTHY")
          .containsEntry("orders", "HEALTHY");
      })
      .verifyComplete();
  }

  private void stub(String service, String endpoint, TopologyStatus status) {
    var info = new TopologyInformation();
    info.setServiceName(service);
    info.setEndpoint(endpoint);
    info.setStatus(status);
    when(topologyService.getTopologyForService(service, endpoint)).thenReturn(
      Mono.just(info)
    );
  }

  private static void inject(Object target, String field, Object value)
    throws Exception {
    var f = target.getClass().getDeclaredField(field);
    f.setAccessible(true);
    f.set(target, value);
  }
}
