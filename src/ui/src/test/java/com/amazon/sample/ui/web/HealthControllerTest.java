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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazon.sample.ui.config.EndpointProperties;
import com.amazon.sample.ui.web.util.TopologyInformation;
import com.amazon.sample.ui.web.util.TopologyService;
import com.amazon.sample.ui.web.util.TopologyStatus;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

class HealthControllerTest {

  @Test
  @SuppressWarnings("unchecked")
  void returnsOkWhenNoBackendIsUnhealthy() {
    var controller = buildController(
      Map.of(
        "catalog",
        TopologyStatus.HEALTHY,
        "carts",
        TopologyStatus.HEALTHY,
        "checkout",
        TopologyStatus.NONE,
        "orders",
        TopologyStatus.HEALTHY
      )
    );

    var response = controller.health().block();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("status", "UP");
    assertThat(
      (Map<String, TopologyStatus>) response.getBody().get("components")
    )
      .containsEntry("catalog", TopologyStatus.HEALTHY)
      .containsEntry("checkout", TopologyStatus.NONE);
  }

  @Test
  void returnsServiceUnavailableWhenAnyBackendIsUnhealthy() {
    var controller = buildController(
      Map.of(
        "catalog",
        TopologyStatus.HEALTHY,
        "carts",
        TopologyStatus.UNHEALTHY,
        "checkout",
        TopologyStatus.HEALTHY,
        "orders",
        TopologyStatus.HEALTHY
      )
    );

    var response = controller.health().block();

    assertThat(response.getStatusCode()).isEqualTo(
      HttpStatus.SERVICE_UNAVAILABLE
    );
    assertThat(response.getBody()).containsEntry("status", "DOWN");
  }

  private HealthController buildController(
    Map<String, TopologyStatus> statuses
  ) {
    var endpoints = new EndpointProperties();
    endpoints.setCatalog("http://catalog");
    endpoints.setCarts("http://carts");
    endpoints.setCheckout("http://checkout");
    endpoints.setOrders("http://orders");

    var topologyService = mock(TopologyService.class);
    when(topologyService.getTopologyForService(anyString(), anyString())).then(
      invocation -> {
        String name = invocation.getArgument(0);
        var info = new TopologyInformation();
        info.setServiceName(name);
        info.setStatus(statuses.getOrDefault(name, TopologyStatus.NONE));
        info.setMetadata(new HashMap<>());
        return Mono.just(info);
      }
    );

    var controller = new HealthController();
    ReflectionTestUtils.setField(controller, "endpoints", endpoints);
    ReflectionTestUtils.setField(
      controller,
      "topologyService",
      topologyService
    );
    return controller;
  }
}
