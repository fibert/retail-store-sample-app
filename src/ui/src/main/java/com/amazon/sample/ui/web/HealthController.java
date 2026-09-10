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
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Deep health check for the UI component. Pings the backend components the UI
 * depends on and returns HTTP 200 only when all of them are healthy, otherwise
 * HTTP 503.
 */
@RestController
public class HealthController {

  @Autowired
  private EndpointProperties endpoints;

  @Autowired
  private TopologyService topologyService;

  @GetMapping("/health")
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
      .collectList()
      .map(this::toResponse);
  }

  private ResponseEntity<Map<String, Object>> toResponse(
    List<TopologyInformation> components
  ) {
    boolean healthy = components
      .stream()
      .noneMatch(info -> info.getStatus() == TopologyStatus.UNHEALTHY);

    Map<String, Object> componentStatuses = new LinkedHashMap<>();
    for (TopologyInformation info : components) {
      componentStatuses.put(info.getServiceName(), info.getStatus().name());
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", healthy ? "UP" : "DOWN");
    body.put("components", componentStatuses);

    return ResponseEntity.status(
      healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE
    ).body(body);
  }
}
