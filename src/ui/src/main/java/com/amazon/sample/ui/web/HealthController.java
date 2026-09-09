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
import com.amazon.sample.ui.web.util.TopologyService;
import com.amazon.sample.ui.web.util.TopologyStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Deep health check for the UI component. Pings the configured backend
 * components and only reports the UI as healthy (HTTP 200) when none of them
 * are unhealthy. If any backend is unreachable the endpoint responds with HTTP
 * 503 so that orchestrators treat the UI as unavailable.
 */
@RestController
@RequestMapping("/health")
@Slf4j
public class HealthController {

  @Autowired
  private EndpointProperties endpoints;

  @Autowired
  private TopologyService topologyService;

  @GetMapping
  public Mono<ResponseEntity<Map<String, String>>> health() {
    return Flux.mergeSequential(
      probe("catalog", endpoints.getCatalog()),
      probe("carts", endpoints.getCarts()),
      probe("checkout", endpoints.getCheckout()),
      probe("orders", endpoints.getOrders()),
      probe("recommendations", endpoints.getRecommendations())
    )
      .collectList()
      .map(components -> {
        var body = new LinkedHashMap<String, String>();
        boolean healthy = true;

        for (Tuple2<String, TopologyStatus> component : components) {
          body.put(component.getT1(), component.getT2().toString());
          if (component.getT2() == TopologyStatus.UNHEALTHY) {
            healthy = false;
          }
        }

        body.put("status", healthy ? "UP" : "DOWN");

        if (healthy) {
          return ResponseEntity.ok(body);
        }

        log.warn("Deep health check failed: {}", body);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
      });
  }

  private Mono<Tuple2<String, TopologyStatus>> probe(
    String serviceName,
    String endpoint
  ) {
    return topologyService
      .checkComponentHealth(serviceName, endpoint)
      .map(status -> Tuples.of(serviceName, status));
  }
}
