package com.saas.gateway.controller;

import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

@RestController
@RequestMapping("/fallback")
public class ServiceFallbackController {
        private static final Logger logger = LoggerFactory.getLogger(ServiceFallbackController.class);

    @RequestMapping("/{service}")
    public ResponseEntity<Map<String, Object>> serviceUnavailable(
            @PathVariable("service") String service, ServerWebExchange exchange) {
        Map<String, Object> response = Map.of(
                "error", "upstream_service_unavailable",
                "service", service,
                "traceId", exchange.getRequest().getHeaders().getFirst("X-Trace-Id"),
                "timestamp", Instant.now().toString());
        logger.warn("Upstream unavailable service={} traceId={}",
                service, exchange.getRequest().getHeaders().getFirst("X-Trace-Id"));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "5")
                .body(response);
    }
}