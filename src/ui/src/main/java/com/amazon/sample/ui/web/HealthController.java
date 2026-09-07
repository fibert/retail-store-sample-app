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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Deep health check for the UI component. Pings every configured backend
 * component and returns 200 only when all of them are healthy, otherwise 503.
 */
@RestController
@Slf4j
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
      topologyService.getTopologyForService("orders", endpoints.getOrders()),
      topologyService.getTopologyForService(
        "recommendations",
        endpoints.getRecommendations()
      )
    )
      .collectList()
      .map(components -> {
        var services = new LinkedHashMap<String, Object>();
        boolean healthy = true;

        for (TopologyInformation component : components) {
          // Only configured backends participate in the deep health check.
          if (component.getStatus() == TopologyStatus.NONE) {
            continue;
          }
          services.put(
            component.getServiceName(),
            component.getStatus().name()
          );
          if (component.getStatus() != TopologyStatus.HEALTHY) {
            healthy = false;
          }
        }

        var body = new LinkedHashMap<String, Object>();
        body.put("status", healthy ? "UP" : "DOWN");
        body.put("components", services);

        if (!healthy) {
          log.warn("Deep health check failed: {}", services);
        }

        return ResponseEntity.status(
          healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE
        ).body((Map<String, Object>) body);
      });
  }
}
