package com.shopMeta.order_service.config;

import feign.FeignException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Retry and circuit-breaker policies for the four downstream services (spec 5.8 / 19).
 *
 * <p>Built programmatically rather than with {@code @Retry} / {@code @CircuitBreaker} annotations so
 * the decoration order is explicit and testable: the retry wraps the breaker, so a transient blip is
 * retried <em>before</em> it counts against the breaker, while an open breaker fails fast instead of
 * hammering a service that is already down.
 *
 * <p>One breaker per downstream service, so a flaky inventory-service cannot also cut off the
 * address lookups from auth-service.
 */
@Configuration
public class ResilienceConfig {

    public static final String PRODUCT_SERVICE = "product-service";
    public static final String INVENTORY_SERVICE = "inventory-service";
    public static final String AUTH_SERVICE = "auth-service";
    public static final String CART_SERVICE = "cart-service";
    public static final String DOWNSTREAM_RETRY = "downstream";

    /**
     * Retry only what is worth retrying: connection failures, timeouts and 5xx. A 4xx means the
     * request itself was wrong, so repeating it would just fail again.
     *
     * <p>Note on the reserve call: it is <strong>idempotent per orderId</strong> (D-053), which is
     * what makes retrying it safe — a retried reserve returns the existing reservation instead of
     * holding stock twice.
     */
    static boolean isRetryable(Throwable throwable) {

        if (throwable instanceof CallNotPermittedException) {
            return false;
        }
        if (throwable instanceof FeignException feignException) {
            // status() is -1 for connection errors, so those stay retryable.
            return feignException.status() <= 0 || feignException.status() >= 500;
        }
        return throwable instanceof IOException
                || throwable instanceof TimeoutException
                || throwable instanceof IllegalStateException;
    }

    @Bean
    public Retry downstreamRetry(ResilienceProperties properties) {

        RetryConfig config = RetryConfig.custom()
                .maxAttempts(properties.getRetryMaxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        properties.getRetryWaitDuration(), properties.getRetryBackoffMultiplier()))
                .retryOnException(ResilienceConfig::isRetryable)
                .ignoreExceptions(CallNotPermittedException.class)
                .build();

        return Retry.of(DOWNSTREAM_RETRY, config);
    }

    @Bean
    public CircuitBreaker productServiceCircuitBreaker(ResilienceProperties properties) {
        return CircuitBreaker.of(PRODUCT_SERVICE, circuitBreakerConfig(properties));
    }

    @Bean
    public CircuitBreaker inventoryServiceCircuitBreaker(ResilienceProperties properties) {
        return CircuitBreaker.of(INVENTORY_SERVICE, circuitBreakerConfig(properties));
    }

    @Bean
    public CircuitBreaker authServiceCircuitBreaker(ResilienceProperties properties) {
        return CircuitBreaker.of(AUTH_SERVICE, circuitBreakerConfig(properties));
    }

    @Bean
    public CircuitBreaker cartServiceCircuitBreaker(ResilienceProperties properties) {
        return CircuitBreaker.of(CART_SERVICE, circuitBreakerConfig(properties));
    }

    private static CircuitBreakerConfig circuitBreakerConfig(ResilienceProperties properties) {

        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.getSlidingWindowSize())
                .minimumNumberOfCalls(properties.getMinimumNumberOfCalls())
                .failureRateThreshold(properties.getFailureRateThreshold())
                .waitDurationInOpenState(properties.getWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(
                        properties.getPermittedNumberOfCallsInHalfOpenState())
                // A 4xx (409 out-of-stock, 404 unknown address, 422 expired reservation) is a
                // legitimate business answer, not a sign that the downstream service is unhealthy —
                // it must not feed the failure rate.
                .ignoreExceptions(FeignException.FeignClientException.class)
                .build();
    }
}
