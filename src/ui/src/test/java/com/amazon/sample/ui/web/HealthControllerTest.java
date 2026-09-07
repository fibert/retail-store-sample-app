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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

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
    endpoints.setCheckout("http://checkout");
    endpoints.setOrders("http://orders");
    endpoints.setRecommendations("http://recommendations");

    topologyService = mock(TopologyService.class);
    lenient()
      .when(
        topologyService.getTopologyForService(
          org.mockito.ArgumentMatchers.anyString(),
          org.mockito.ArgumentMatchers.anyString()
        )
      )
      .thenAnswer(invocation ->
        Mono.just(info(invocation.getArgument(0), TopologyStatus.HEALTHY))
      );

    controller = new HealthController();
    inject(controller, "endpoints", endpoints);
    inject(controller, "topologyService", topologyService);
  }

  @Test
  @SuppressWarnings("unchecked")
  void returnsOkWhenAllBackendsHealthy() {
    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).containsEntry("status", "UP");
        Map<String, Object> components = (Map<String, Object>) body.get(
          "components"
        );
        assertThat(components).hasSize(5);
        assertThat(components.values()).allMatch("HEALTHY"::equals);
      })
      .verifyComplete();
  }

  @Test
  @SuppressWarnings("unchecked")
  void returnsServiceUnavailableWhenAnyBackendUnhealthy() {
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
        Map<String, Object> body = response.getBody();
        assertThat(body).containsEntry("status", "DOWN");
        Map<String, Object> components = (Map<String, Object>) body.get(
          "components"
        );
        assertThat(components).containsEntry("carts", "UNHEALTHY");
      })
      .verifyComplete();
  }

  @Test
  @SuppressWarnings("unchecked")
  void ignoresUnconfiguredBackends() {
    endpoints.setCheckout(null);
    endpoints.setRecommendations("");
    stub("catalog", TopologyStatus.HEALTHY);
    stub("carts", TopologyStatus.HEALTHY);
    stub("checkout", TopologyStatus.NONE);
    stub("orders", TopologyStatus.HEALTHY);
    stub("recommendations", TopologyStatus.NONE);

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> components = (Map<String, Object>) response
          .getBody()
          .get("components");
        assertThat(components).hasSize(3);
        assertThat(components).doesNotContainKey("checkout");
        assertThat(components).doesNotContainKey("recommendations");
      })
      .verifyComplete();
  }

  private void stub(String service, TopologyStatus status) {
    lenient()
      .when(
        topologyService.getTopologyForService(
          org.mockito.ArgumentMatchers.eq(service),
          org.mockito.ArgumentMatchers.any()
        )
      )
      .thenReturn(Mono.just(info(service, status)));
  }

  private static TopologyInformation info(
    String service,
    TopologyStatus status
  ) {
    var topology = new TopologyInformation();
    topology.setServiceName(service);
    topology.setMetadata(new HashMap<>());
    topology.setStatus(status);
    return topology;
  }

  private static void inject(Object target, String field, Object value) {
    try {
      var f = target.getClass().getDeclaredField(field);
      f.setAccessible(true);
      f.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
