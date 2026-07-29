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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Top-level deep health check for the UI service. Pings each downstream
 * dependency and returns a JSON summary of their statuses, responding with
 * HTTP 200 when every configured dependency is reachable or HTTP 503 when any
 * of them is down.
 */
@RestController
public class HealthController {

  private static final String STATUS_UP = "UP";
  private static final String STATUS_DOWN = "DOWN";
  private static final String STATUS_UNKNOWN = "UNKNOWN";

  private final EndpointProperties endpoints;
  private final TopologyService topologyService;

  public HealthController(
    EndpointProperties endpoints,
    TopologyService topologyService
  ) {
    this.endpoints = endpoints;
    this.topologyService = topologyService;
  }

  @GetMapping("/health")
  public Mono<ResponseEntity<Map<String, Object>>> health() {
    return Flux.merge(
      checkDependency("catalog", endpoints.getCatalog()),
      checkDependency("carts", endpoints.getCarts()),
      checkDependency("orders", endpoints.getOrders()),
      checkDependency("checkout", endpoints.getCheckout())
    )
      .collectMap(DependencyHealth::name, DependencyHealth::status)
      .map(this::buildResponse);
  }

  private Mono<DependencyHealth> checkDependency(String name, String endpoint) {
    return topologyService
      .getTopologyForService(name, endpoint)
      .map(info -> new DependencyHealth(name, mapStatus(info)));
  }

  private String mapStatus(TopologyInformation info) {
    if (info.getStatus() == TopologyStatus.HEALTHY) {
      return STATUS_UP;
    }
    if (info.getStatus() == TopologyStatus.UNHEALTHY) {
      return STATUS_DOWN;
    }
    return STATUS_UNKNOWN;
  }

  private ResponseEntity<Map<String, Object>> buildResponse(
    Map<String, String> dependencies
  ) {
    boolean anyDown = dependencies.containsValue(STATUS_DOWN);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", anyDown ? STATUS_DOWN : STATUS_UP);
    body.put("dependencies", dependencies);

    return ResponseEntity.status(
      anyDown ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK
    ).body(body);
  }

  private record DependencyHealth(String name, String status) {}
}
