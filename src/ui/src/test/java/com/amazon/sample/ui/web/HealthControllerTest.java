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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class HealthControllerTest {

  @Test
  @SuppressWarnings("unchecked")
  void returnsOkWhenAllConfiguredBackendsAreHealthy() {
    var topologyService = mock(TopologyService.class);
    stub(topologyService, "catalog", TopologyStatus.HEALTHY);
    stub(topologyService, "carts", TopologyStatus.HEALTHY);
    stub(topologyService, "checkout", TopologyStatus.HEALTHY);
    stub(topologyService, "orders", TopologyStatus.HEALTHY);
    stub(topologyService, "recommendations", TopologyStatus.NONE);

    var controller = new HealthController(endpoints(), topologyService);

    StepVerifier.create(controller.health())
      .assertNext(entity -> {
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = (Map<String, Object>) entity.getBody();
        assertThat(body).containsEntry("status", "UP");
      })
      .verifyComplete();
  }

  @Test
  @SuppressWarnings("unchecked")
  void returnsServiceUnavailableWhenABackendIsUnhealthy() {
    var topologyService = mock(TopologyService.class);
    stub(topologyService, "catalog", TopologyStatus.HEALTHY);
    stub(topologyService, "carts", TopologyStatus.UNHEALTHY);
    stub(topologyService, "checkout", TopologyStatus.HEALTHY);
    stub(topologyService, "orders", TopologyStatus.HEALTHY);
    stub(topologyService, "recommendations", TopologyStatus.HEALTHY);

    var controller = new HealthController(endpoints(), topologyService);

    StepVerifier.create(controller.health())
      .assertNext(entity -> {
        assertThat(entity.getStatusCode()).isEqualTo(
          HttpStatus.SERVICE_UNAVAILABLE
        );
        Map<String, Object> body = (Map<String, Object>) entity.getBody();
        assertThat(body).containsEntry("status", "DOWN");
        assertThat((Map<String, String>) body.get("services")).containsEntry(
          "carts",
          "UNHEALTHY"
        );
      })
      .verifyComplete();
  }

  private void stub(
    TopologyService topologyService,
    String service,
    TopologyStatus status
  ) {
    var info = new TopologyInformation();
    info.setServiceName(service);
    info.setStatus(status);
    when(
      topologyService.getTopologyForService(eq(service), anyString())
    ).thenReturn(Mono.just(info));
  }

  private EndpointProperties endpoints() {
    var endpoints = new EndpointProperties();
    endpoints.setCatalog("http://catalog");
    endpoints.setCarts("http://carts");
    endpoints.setCheckout("http://checkout");
    endpoints.setOrders("http://orders");
    endpoints.setRecommendations("http://recommendations");
    return endpoints;
  }
}
