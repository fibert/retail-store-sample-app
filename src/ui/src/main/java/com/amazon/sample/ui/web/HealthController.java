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

import com.amazon.sample.ui.config.EndpointProperties;
import com.amazon.sample.ui.web.util.TopologyInformation;
import com.amazon.sample.ui.web.util.TopologyService;
import com.amazon.sample.ui.web.util.TopologyStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Top-level deep health check for the UI service. Pings each downstream
 * dependency and returns 200 with a JSON summary of every dependency's status,
 * or 503 if any dependency is down.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

  private final EndpointProperties endpoints;
  private final TopologyService topologyService;

  @Autowired
  public HealthController(
    EndpointProperties endpoints,
    TopologyService topologyService
  ) {
    this.endpoints = endpoints;
    this.topologyService = topologyService;
  }

  @GetMapping
  public Mono<ResponseEntity<Map<String, Object>>> health() {
    return Flux.merge(
      check("catalog", endpoints.getCatalog()),
      check("cart", endpoints.getCarts()),
      check("orders", endpoints.getOrders()),
      check("checkout", endpoints.getCheckout())
    )
      .collectMap(TopologyInformation::getServiceName, this::describe)
      .map(dependencies -> {
        boolean allHealthy = dependencies
          .values()
          .stream()
          .noneMatch("DOWN"::equals);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", allHealthy ? "UP" : "DOWN");
        body.put("dependencies", dependencies);

        return ResponseEntity.status(
          allHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE
        ).body(body);
      });
  }

  private Mono<TopologyInformation> check(String serviceName, String endpoint) {
    return topologyService.getTopologyForService(serviceName, endpoint);
  }

  private String describe(TopologyInformation info) {
    if (info.getStatus() == TopologyStatus.HEALTHY) {
      return "UP";
    }
    if (info.getStatus() == TopologyStatus.UNHEALTHY) {
      return "DOWN";
    }
    return "UNKNOWN";
  }
}
