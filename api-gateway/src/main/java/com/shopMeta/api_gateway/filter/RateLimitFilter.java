package com.shopMeta.api_gateway.filter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.shopMeta.api_gateway.config.GatewayProperties;
import com.shopMeta.api_gateway.error.GatewayErrorWriter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * In-memory rate limiter for the credential endpoints (spec 6.2 / 18: "5/min per IP on
 * login/register/forgot-password").
 *
 * <p>Fixed-window counter keyed by {@code clientIp|path}. Deliberately simple and dependency free:
 * the spec allows "Redis or in-memory" and Redis is not part of the platform yet, so the counter
 * lives in this JVM. It will move to the Redis-backed
 * {@code RequestRateLimiter} once Redis is available, which also makes it correct across gateway
 * replicas — see docs/DECISIONS.md.
 *
 * <p>Runs after the correlation id and before JWT validation, so brute-forcing is rejected before
 * any token work happens.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 200;

    /** Above this many tracked keys, idle buckets are purged. */
    private static final int PURGE_THRESHOLD = 10_000;

    private final GatewayProperties properties;
    private final GatewayErrorWriter errorWriter;

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        GatewayProperties.RateLimit config = properties.getRateLimit();
        if (!config.isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();
        if (!isRateLimited(path, config)) {
            return chain.filter(exchange);
        }

        String key = clientIp(exchange) + "|" + path;
        if (!isAllowed(key, config)) {
            log.warn("Rate limit exceeded for {} on {} [cid={}]", clientIp(exchange), path,
                    exchange.getAttributes().get(CorrelationIdFilter.CORRELATION_ID_ATTR));
            exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(config.getWindowSeconds()));
            return errorWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                    "Too many requests. Please try again later.");
        }

        return chain.filter(exchange);
    }

    private boolean isRateLimited(String path, GatewayProperties.RateLimit config) {
        for (String limited : config.getPaths()) {
            if (path.equals(limited) || path.startsWith(limited + "/")) {
                return true;
            }
        }
        return false;
    }

    /** Fixed-window: allow {@code requestsPerWindow} hits per rolling window. */
    private boolean isAllowed(String key, GatewayProperties.RateLimit config) {
        if (windows.size() > PURGE_THRESHOLD) {
            purgeExpired(config);
        }

        long now = System.currentTimeMillis();
        long windowStart = now - config.getWindowSeconds() * 1000L;

        Deque<Long> hits = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst() < windowStart) {
                hits.pollFirst();
            }
            if (hits.size() >= config.getRequestsPerWindow()) {
                return false;
            }
            hits.addLast(now);
            return true;
        }
    }

    private void purgeExpired(GatewayProperties.RateLimit config) {
        long windowStart = System.currentTimeMillis() - config.getWindowSeconds() * 1000L;
        windows.entrySet().removeIf(entry -> {
            Deque<Long> hits = entry.getValue();
            synchronized (hits) {
                while (!hits.isEmpty() && hits.peekFirst() < windowStart) {
                    hits.pollFirst();
                }
                return hits.isEmpty();
            }
        });
    }

    private String clientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

}
