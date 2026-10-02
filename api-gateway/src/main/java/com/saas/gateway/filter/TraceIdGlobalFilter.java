package com.saas.gateway.filter;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {
    private static final Logger logger = LoggerFactory.getLogger(TraceIdGlobalFilter.class);
    private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final Pattern VALID_TRACEPARENT =
            Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceParent = exchange.getRequest().getHeaders().getFirst("traceparent");
        String incoming = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        var traceParentMatch = traceParent == null ? null : VALID_TRACEPARENT.matcher(traceParent);
        String traceId = traceParentMatch != null && traceParentMatch.matches()
            ? traceParentMatch.group(1)
            : incoming != null && VALID_TRACE_ID.matcher(incoming).matches()
                ? incoming : UUID.randomUUID().toString();
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header("X-Trace-Id", traceId).build();
        logger.debug("Gateway request method={} path={} traceId={}",
            request.getMethod(), request.getURI().getPath(), traceId);
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);
            return Mono.empty();
        });
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}