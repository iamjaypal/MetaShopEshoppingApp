package com.shopMeta.api_gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopMeta.api_gateway.config.GatewayProperties;
import com.shopMeta.api_gateway.error.GatewayErrorWriter;

import reactor.core.publisher.Mono;

class RateLimitFilterTest {

    private GatewayProperties properties;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new GatewayProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setRequestsPerWindow(2);
        properties.getRateLimit().setWindowSeconds(60);
        properties.getRateLimit().setPaths(List.of("/api/v1/auth/login"));

        filter = new RateLimitFilter(properties, new GatewayErrorWriter(new ObjectMapper()));
    }

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path).build());
    }

    private int callsUntilLimited(String path) {
        AtomicInteger calls = new AtomicInteger();
        GatewayFilterChain chain = ex -> {
            calls.incrementAndGet();
            return Mono.empty();
        };

        for (int i = 0; i < 5; i++) {
            MockServerWebExchange exchange = exchange(path);
            filter.filter(exchange, chain).block();
            if (exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                return calls.get();
            }
        }
        return calls.get();
    }

    @Test
    void allowsUpToTheLimitThenRejectsWith429() {
        // 2 allowed, the 3rd is rejected
        assertThat(callsUntilLimited("/api/v1/auth/login")).isEqualTo(2);
    }

    @Test
    void rejectionCarriesRetryAfterHeader() {
        GatewayFilterChain chain = ex -> Mono.empty();

        filter.filter(exchange("/api/v1/auth/login"), chain).block();
        filter.filter(exchange("/api/v1/auth/login"), chain).block();

        MockServerWebExchange third = exchange("/api/v1/auth/login");
        filter.filter(third, chain).block();

        assertThat(third.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(third.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(third.getResponse().getBodyAsString().block()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void pathsOutsideTheListAreNotLimited() {
        assertThat(callsUntilLimited("/api/v1/products")).isEqualTo(5);
    }

    @Test
    void limitingIsPerPath() {
        GatewayFilterChain chain = ex -> Mono.empty();

        filter.filter(exchange("/api/v1/auth/login"), chain).block();
        filter.filter(exchange("/api/v1/auth/login"), chain).block();

        // login is now exhausted, but another path still works
        MockServerWebExchange other = exchange("/api/v1/auth/register");
        properties.getRateLimit().setPaths(List.of("/api/v1/auth/login", "/api/v1/auth/register"));
        filter.filter(other, chain).block();

        assertThat(other.getResponse().getStatusCode()).isNull();
    }

    @Test
    void canBeDisabled() {
        properties.getRateLimit().setEnabled(false);

        assertThat(callsUntilLimited("/api/v1/auth/login")).isEqualTo(5);
    }

}
