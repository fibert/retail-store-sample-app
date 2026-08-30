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
import static org.mockito.ArgumentMatchers.any;
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

    topologyService = mock(TopologyService.class);
    when(topologyService.getTopologyForService(any(), any())).thenAnswer(
      invocation ->
        Mono.just(info(invocation.getArgument(0), TopologyStatus.NONE))
    );

    controller = new HealthController();
    inject(controller, "endpoints", endpoints);
    inject(controller, "topologyService", topologyService);
  }

  @Test
  void returnsOkWhenAllConfiguredComponentsHealthy() {
    when(
      topologyService.getTopologyForService(eq("catalog"), any())
    ).thenReturn(Mono.just(info("catalog", TopologyStatus.HEALTHY)));
    when(topologyService.getTopologyForService(eq("carts"), any())).thenReturn(
      Mono.just(info("carts", TopologyStatus.HEALTHY))
    );

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
        @SuppressWarnings("unchecked")
        var components = (Map<String, Object>) response
          .getBody()
          .get("components");
        assertThat(components)
          .containsEntry("catalog", "UP")
          .containsEntry("carts", "UP");
      })
      .verifyComplete();
  }

  @Test
  void returnsServiceUnavailableWhenAnyComponentUnhealthy() {
    when(
      topologyService.getTopologyForService(eq("catalog"), any())
    ).thenReturn(Mono.just(info("catalog", TopologyStatus.HEALTHY)));
    when(topologyService.getTopologyForService(eq("carts"), any())).thenReturn(
      Mono.just(info("carts", TopologyStatus.UNHEALTHY))
    );

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(
          HttpStatus.SERVICE_UNAVAILABLE
        );
        assertThat(response.getBody()).containsEntry("status", "DOWN");
        @SuppressWarnings("unchecked")
        var components = (Map<String, Object>) response
          .getBody()
          .get("components");
        assertThat(components).containsEntry("carts", "DOWN");
      })
      .verifyComplete();
  }

  @Test
  void returnsOkWhenNoBackendsConfigured() {
    // All services fall back to the default NONE stub from setUp().
    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
        @SuppressWarnings("unchecked")
        var components = (Map<String, Object>) response
          .getBody()
          .get("components");
        assertThat(components).isEmpty();
      })
      .verifyComplete();
  }

  private static TopologyInformation info(
    String serviceName,
    TopologyStatus status
  ) {
    var topology = new TopologyInformation();
    topology.setServiceName(serviceName);
    topology.setStatus(status);
    return topology;
  }

  private static void inject(Object target, String field, Object value) {
    try {
      var f = target.getClass().getDeclaredField(field);
      f.setAccessible(true);
      f.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
