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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazon.sample.ui.config.EndpointProperties;
import com.amazon.sample.ui.web.util.TopologyInformation;
import com.amazon.sample.ui.web.util.TopologyService;
import com.amazon.sample.ui.web.util.TopologyStatus;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class HealthControllerTest {

  private TopologyService topologyService;
  private EndpointProperties endpoints;
  private WebTestClient client;

  @BeforeEach
  void setUp() {
    topologyService = mock(TopologyService.class);
    endpoints = new EndpointProperties();
    endpoints.setCatalog("http://catalog");
    endpoints.setCarts("http://carts");
    endpoints.setCheckout("http://checkout");
    endpoints.setOrders("http://orders");
    endpoints.setRecommendations("http://recommendations");

    HealthController controller = new HealthController(
      endpoints,
      topologyService
    );

    client = WebTestClient.bindToController(controller).build();
  }

  private void stub(String service, String endpoint, TopologyStatus status) {
    var info = new TopologyInformation(
      service,
      endpoint,
      new HashMap<>(),
      status
    );
    when(
      topologyService.getTopologyForService(eq(service), eq(endpoint))
    ).thenReturn(Mono.just(info));
  }

  @Test
  void returns200WhenAllBackendsHealthy() {
    stub("catalog", "http://catalog", TopologyStatus.HEALTHY);
    stub("carts", "http://carts", TopologyStatus.HEALTHY);
    stub("checkout", "http://checkout", TopologyStatus.HEALTHY);
    stub("orders", "http://orders", TopologyStatus.HEALTHY);
    stub("recommendations", "http://recommendations", TopologyStatus.HEALTHY);

    client
      .get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk()
      .expectBody()
      .jsonPath("$.status")
      .isEqualTo("UP")
      .jsonPath("$.services.catalog")
      .isEqualTo("UP");
  }

  @Test
  void returns503WhenAnyBackendUnhealthy() {
    stub("catalog", "http://catalog", TopologyStatus.HEALTHY);
    stub("carts", "http://carts", TopologyStatus.HEALTHY);
    stub("checkout", "http://checkout", TopologyStatus.UNHEALTHY);
    stub("orders", "http://orders", TopologyStatus.HEALTHY);
    stub("recommendations", "http://recommendations", TopologyStatus.HEALTHY);

    client
      .get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isEqualTo(503)
      .expectBody()
      .jsonPath("$.status")
      .isEqualTo("DOWN")
      .jsonPath("$.services.checkout")
      .isEqualTo("DOWN");
  }

  @Test
  void returns200WhenBackendUnconfigured() {
    stub("catalog", "http://catalog", TopologyStatus.HEALTHY);
    stub("carts", "http://carts", TopologyStatus.HEALTHY);
    stub("checkout", "http://checkout", TopologyStatus.HEALTHY);
    stub("orders", "http://orders", TopologyStatus.HEALTHY);
    stub("recommendations", "http://recommendations", TopologyStatus.NONE);

    client
      .get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk()
      .expectBody()
      .jsonPath("$.status")
      .isEqualTo("UP")
      .jsonPath("$.services.recommendations")
      .isEqualTo("UNKNOWN");
  }
}
