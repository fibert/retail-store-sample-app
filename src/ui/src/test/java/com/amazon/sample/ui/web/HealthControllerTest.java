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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class HealthControllerTest {

  @Test
  @SuppressWarnings("unchecked")
  void returnsOkWhenAllConfiguredDependenciesAreHealthy() {
    var endpoints = endpoints(
      "http://catalog",
      "http://carts",
      "http://checkout",
      null
    );
    var topologyService = mock(TopologyService.class);
    stub(topologyService, "catalog", "http://catalog", TopologyStatus.HEALTHY);
    stub(topologyService, "carts", "http://carts", TopologyStatus.HEALTHY);
    stub(
      topologyService,
      "checkout",
      "http://checkout",
      TopologyStatus.HEALTHY
    );
    stub(topologyService, "orders", null, TopologyStatus.NONE);

    var controller = new HealthController(endpoints, topologyService);

    StepVerifier.create(controller.health())
      .assertNext(entity -> {
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody().get("status")).isEqualTo("UP");
        var deps = (Map<String, Object>) entity.getBody().get("dependencies");
        assertThat(deps)
          .containsEntry("catalog", "UP")
          .containsEntry("carts", "UP")
          .containsEntry("checkout", "UP")
          .containsEntry("orders", "DISABLED");
      })
      .verifyComplete();
  }

  @Test
  @SuppressWarnings("unchecked")
  void returnsServiceUnavailableWhenADependencyIsDown() {
    var endpoints = endpoints(
      "http://catalog",
      "http://carts",
      "http://checkout",
      "http://orders"
    );
    var topologyService = mock(TopologyService.class);
    stub(topologyService, "catalog", "http://catalog", TopologyStatus.HEALTHY);
    stub(topologyService, "carts", "http://carts", TopologyStatus.HEALTHY);
    stub(
      topologyService,
      "checkout",
      "http://checkout",
      TopologyStatus.HEALTHY
    );
    stub(topologyService, "orders", "http://orders", TopologyStatus.UNHEALTHY);

    var controller = new HealthController(endpoints, topologyService);

    StepVerifier.create(controller.health())
      .assertNext(entity -> {
        assertThat(entity.getStatusCode()).isEqualTo(
          HttpStatus.SERVICE_UNAVAILABLE
        );
        assertThat(entity.getBody().get("status")).isEqualTo("DOWN");
        var deps = (Map<String, Object>) entity.getBody().get("dependencies");
        assertThat(deps)
          .containsEntry("catalog", "UP")
          .containsEntry("orders", "DOWN");
      })
      .verifyComplete();
  }

  @Test
  void unconfiguredDependenciesDoNotFailHealth() {
    var endpoints = endpoints(null, null, null, null);
    var topologyService = mock(TopologyService.class);
    stub(topologyService, "catalog", null, TopologyStatus.NONE);
    stub(topologyService, "carts", null, TopologyStatus.NONE);
    stub(topologyService, "checkout", null, TopologyStatus.NONE);
    stub(topologyService, "orders", null, TopologyStatus.NONE);

    var controller = new HealthController(endpoints, topologyService);

    StepVerifier.create(controller.health())
      .assertNext(entity ->
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK)
      )
      .verifyComplete();
  }

  private void stub(
    TopologyService topologyService,
    String service,
    String endpoint,
    TopologyStatus status
  ) {
    var info = new TopologyInformation();
    info.setServiceName(service);
    info.setEndpoint(endpoint);
    info.setStatus(status);
    when(
      topologyService.getTopologyForService(eq(service), eq(endpoint))
    ).thenReturn(Mono.just(info));
  }

  private EndpointProperties endpoints(
    String catalog,
    String carts,
    String checkout,
    String orders
  ) {
    var properties = new EndpointProperties();
    properties.setCatalog(catalog);
    properties.setCarts(carts);
    properties.setCheckout(checkout);
    properties.setOrders(orders);
    return properties;
  }
}
