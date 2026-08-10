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
import com.amazon.sample.ui.web.util.TopologyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
    topologyService = Mockito.mock(TopologyService.class);
    endpoints = new EndpointProperties();
    endpoints.setCatalog("http://catalog");
    endpoints.setCarts("http://carts");
    endpoints.setCheckout("http://checkout");
    endpoints.setOrders("http://orders");

    controller = new HealthController();
    ReflectionTestUtils.setField(
      controller,
      "topologyService",
      topologyService
    );
    ReflectionTestUtils.setField(controller, "endpoints", endpoints);
  }

  @Test
  void returnsOkWhenAllBackendsHealthy() {
    when(topologyService.isServiceHealthy(Mockito.anyString())).thenReturn(
      Mono.just(true)
    );

    StepVerifier.create(controller.health())
      .assertNext(response -> {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("OK");
      })
      .verifyComplete();
  }

  @Test
  void returnsServiceUnavailableWhenAnyBackendUnhealthy() {
    when(topologyService.isServiceHealthy("http://catalog")).thenReturn(
      Mono.just(true)
    );
    when(topologyService.isServiceHealthy("http://carts")).thenReturn(
      Mono.just(false)
    );
    when(topologyService.isServiceHealthy("http://checkout")).thenReturn(
      Mono.just(true)
    );
    when(topologyService.isServiceHealthy("http://orders")).thenReturn(
      Mono.just(true)
    );

    StepVerifier.create(controller.health())
      .assertNext(response ->
        assertThat(response.getStatusCode()).isEqualTo(
          HttpStatus.SERVICE_UNAVAILABLE
        )
      )
      .verifyComplete();
  }
}
