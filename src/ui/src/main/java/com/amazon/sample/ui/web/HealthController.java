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
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Deep health check for the UI. Pings the configured backend components and
 * returns 200 only when all of them are healthy, otherwise 503.
 */
@Controller
@RequestMapping("/health")
@Slf4j
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

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public Mono<ResponseEntity<Map<String, Object>>> health() {
    Map<String, String> configured = new LinkedHashMap<>();
    addIfConfigured(configured, "catalog", endpoints.getCatalog());
    addIfConfigured(configured, "carts", endpoints.getCarts());
    addIfConfigured(configured, "checkout", endpoints.getCheckout());
    addIfConfigured(configured, "orders", endpoints.getOrders());

    return Flux.fromIterable(configured.entrySet())
      .flatMap(entry ->
        topologyService
          .checkHealth(entry.getValue())
          .map(healthy -> Map.entry(entry.getKey(), healthy))
      )
      .collectMap(Map.Entry::getKey, Map.Entry::getValue)
      .map(this::buildResponse);
  }

  private void addIfConfigured(
    Map<String, String> configured,
    String name,
    String endpoint
  ) {
    if (StringUtils.hasText(endpoint)) {
      configured.put(name, endpoint);
    }
  }

  private ResponseEntity<Map<String, Object>> buildResponse(
    Map<String, Boolean> results
  ) {
    boolean allHealthy = results
      .values()
      .stream()
      .allMatch(Boolean::booleanValue);

    Map<String, Object> components = new LinkedHashMap<>();
    results.forEach((name, healthy) ->
      components.put(name, healthy ? "UP" : "DOWN")
    );

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", allHealthy ? "UP" : "DOWN");
    body.put("components", components);

    if (!allHealthy) {
      log.warn("Deep health check failed: {}", components);
    }

    HttpStatus status = allHealthy
      ? HttpStatus.OK
      : HttpStatus.SERVICE_UNAVAILABLE;
    return ResponseEntity.status(status).body(body);
  }
}
