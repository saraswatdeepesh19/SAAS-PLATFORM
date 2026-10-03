package com.saas.gateway.filter;

import com.saas.common.constants.JwtClaims;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TenantRateLimitGlobalFilter implements GlobalFilter, Ordered {
    private static final Logger logger = LoggerFactory.getLogger(TenantRateLimitGlobalFilter.class);
    private final ConcurrentMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final int replenishPerSecond;
    private final int burstCapacity;

    public TenantRateLimitGlobalFilter(
            @Value("${gateway.rate-limit.replenish-per-second}") int replenishPerSecond,
            @Value("${gateway.rate-limit.burst-capacity}") int burstCapacity) {
        this.replenishPerSecond = replenishPerSecond;
        this.burstCapacity = burstCapacity;
    }

    /**
     * Applies a separate token bucket to each authenticated tenant.
     * Per-tenant buckets prevent one customer's traffic from consuming another tenant's allowance.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal().ofType(JwtAuthenticationToken.class)
                .map(authentication -> {
                    String tenantId = authentication.getToken().getClaimAsString(JwtClaims.TENANT_ID);
                    if (tenantId == null || tenantId.isBlank()) {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }
                    TokenBucket bucket = buckets.computeIfAbsent(tenantId,
                            key -> new TokenBucket(burstCapacity, replenishPerSecond));
                    if (!bucket.tryConsume()) {
                        logger.warn("Tenant request rate limited tenantId={} path={}",
                                tenantId, exchange.getRequest().getURI().getPath());
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                })
                .defaultIfEmpty(chain.filter(exchange))
                .flatMap(next -> next);
    }

    /**
     * Runs rate limiting after authentication has populated the principal.
     * The filter needs the verified JWT to identify the tenant before forwarding requests.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private static final class TokenBucket {
        private final int capacity;
        private final int refillPerSecond;
        private double tokens;
        private long lastRefillNanos;

        private TokenBucket(int capacity, int refillPerSecond) {
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        /**
         * Refills tokens according to elapsed time and accepts one request if a token is available.
         * Synchronization prevents concurrent requests from spending the same token twice.
         */
        private synchronized boolean tryConsume() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
            lastRefillNanos = now;
            if (tokens < 1) {
                return false;
            }
            tokens -= 1;
            return true;
        }
    }
}