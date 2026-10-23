package com.shopMeta.cart_service.config;

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
 * Retry and circuit-breaker policies for the two downstream services (spec 5.8 / 19).
 *
 * <p>Built programmatically rather than with {@code @Retry} / {@code @CircuitBreaker} annotations so
 * the decoration order is explicit and testable: the retry wraps the breaker, so a transient blip
 * is retried <em>before</em> it counts against the breaker, while an open breaker fails fast instead
 * of hammering a service that is already down.
 */
@Configuration
public class ResilienceConfig {

    public static final String PRODUCT_SERVICE = "product-service";
    public static final String INVENTORY_SERVICE = "inventory-service";
    public static final String DOWNSTREAM_RETRY = "downstream";

    /**
     * Retry only what is worth retrying: connection failures, timeouts and 5xx. A 4xx means the
     * request itself was wrong, so repeating it would just fail again. Both calls this service
     * makes are idempotent reads, which is what makes retrying them safe (spec 5.8).
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

    private static CircuitBreakerConfig circuitBreakerConfig(ResilienceProperties properties) {

        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.getSlidingWindowSize())
                .minimumNumberOfCalls(properties.getMinimumNumberOfCalls())
                .failureRateThreshold(properties.getFailureRateThreshold())
                .waitDurationInOpenState(properties.getWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(
                        properties.getPermittedNumberOfCallsInHalfOpenState())
                .build();
    }
}
