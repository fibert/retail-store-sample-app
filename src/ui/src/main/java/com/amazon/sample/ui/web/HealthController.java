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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Deep health check for the UI component. Unlike the shallow actuator health
 * endpoint, {@code /health} pings every backend component the UI depends on and
 * only returns 200 when all of them are healthy, otherwise 503.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

  @Autowired
  private EndpointProperties endpoints;

  @Autowired
  private TopologyService topologyService;

  @GetMapping
  public Mono<ResponseEntity<String>> health() {
    return Flux.merge(
      topologyService.isServiceHealthy(endpoints.getCatalog()),
      topologyService.isServiceHealthy(endpoints.getCarts()),
      topologyService.isServiceHealthy(endpoints.getCheckout()),
      topologyService.isServiceHealthy(endpoints.getOrders())
    )
      .all(healthy -> healthy)
      .map(allHealthy ->
        allHealthy
          ? ResponseEntity.ok("OK")
          : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
              "One or more backend components are unhealthy"
            )
      );
  }
}
