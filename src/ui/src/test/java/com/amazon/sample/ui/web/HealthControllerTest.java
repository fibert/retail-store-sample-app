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
import com.amazon.sample.ui.web.util.TopologyService;
import com.amazon.sample.ui.web.util.TopologyStatus;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
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

    controller = new HealthController();
    ReflectionTestUtils.setField(
      controller,
      "topologyService",
      topologyService
    );
    ReflectionTestUtils.setField(controller, "endpoints", endpoints);
  }

  private void stub(String service, TopologyStatus status) {
    when(topologyService.checkComponentHealth(eq(service), any())).thenReturn(
      Mono.just(status)
    );
  }

  @Test
  void returnsOkWhenAllComponentsHealthy() {
    stub("catalog", TopologyStatus.HEALTHY);
    stub("carts", TopologyStatus.HEALTHY);
    stub("checkout", TopologyStatus.HEALTHY);
    stub("orders", TopologyStatus.HEALTHY);
    stub("recommendations", TopologyStatus.HEALTHY);

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, String> body = response.getBody();
        assertThat(body).containsEntry("status", "UP");
        assertThat(body).containsEntry("catalog", "HEALTHY");
      })
      .verifyComplete();
  }

  @Test
  void returnsOkWhenComponentIsNotConfigured() {
    stub("catalog", TopologyStatus.HEALTHY);
    stub("carts", TopologyStatus.HEALTHY);
    stub("checkout", TopologyStatus.HEALTHY);
    stub("orders", TopologyStatus.HEALTHY);
    stub("recommendations", TopologyStatus.NONE);

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
      })
      .verifyComplete();
  }

  @Test
  void returnsServiceUnavailableWhenAComponentIsUnhealthy() {
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
        Map<String, String> body = response.getBody();
        assertThat(body).containsEntry("status", "DOWN");
        assertThat(body).containsEntry("carts", "UNHEALTHY");
      })
      .verifyComplete();
  }
}
