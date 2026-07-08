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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Deep health check that pings each downstream service and reports an aggregate
 * status. Returns 200 when every configured dependency is reachable, or 503 if
 * any of them is down. Dependencies without a configured endpoint are reported
 * as DISABLED and do not affect the overall status.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

  private final EndpointProperties endpoints;

  private final TopologyService topologyService;

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
      topologyService.getTopologyForService("catalog", endpoints.getCatalog()),
      topologyService.getTopologyForService("carts", endpoints.getCarts()),
      topologyService.getTopologyForService(
        "checkout",
        endpoints.getCheckout()
      ),
      topologyService.getTopologyForService("orders", endpoints.getOrders())
    )
      .collectMap(TopologyInformation::getServiceName, info ->
        statusLabel(info.getStatus())
      )
      .map(dependencies -> {
        boolean anyDown = dependencies
          .values()
          .stream()
          .anyMatch("DOWN"::equals);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", anyDown ? "DOWN" : "UP");
        body.put("dependencies", dependencies);

        return ResponseEntity.status(
          anyDown ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK
        ).body(body);
      });
  }

  private static String statusLabel(TopologyStatus status) {
    return switch (status) {
      case HEALTHY -> "UP";
      case UNHEALTHY -> "DOWN";
      case NONE -> "DISABLED";
    };
  }
}
