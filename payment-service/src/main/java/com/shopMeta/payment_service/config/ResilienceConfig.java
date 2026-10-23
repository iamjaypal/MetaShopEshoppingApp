package com.shopMeta.payment_service.config;

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
 * Retry and circuit-breaker policy for the calls to order-service (spec 5.8 / 19).
 *
 * <p>Built programmatically rather than with {@code @Retry} / {@code @CircuitBreaker} annotations so
 * the decoration order is explicit and testable: the retry wraps the breaker, so a transient blip is
 * retried <em>before</em> it counts against the breaker, while an open breaker fails fast instead of
 * hammering a service that is already down.
 *
 * <p>The callback retries are safe because order-service's callbacks are <strong>idempotent</strong>
 * (D-071): a retried {@code payment-confirmed} replays the confirmed order instead of double-charging.
 */
@Configuration
public class ResilienceConfig {

    public static final String ORDER_SERVICE = "order-service";
    public static final String DOWNSTREAM_RETRY = "downstream";

    /**
     * Retry only what is worth retrying: connection failures, timeouts and 5xx. A 4xx means the
     * request itself was wrong (or is a business answer, like the 422 of an already-confirmed
     * order), so repeating it would just fail again.
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
    public CircuitBreaker orderServiceCircuitBreaker(ResilienceProperties properties) {

        return CircuitBreaker.of(ORDER_SERVICE,
                CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(properties.getSlidingWindowSize())
                        .minimumNumberOfCalls(properties.getMinimumNumberOfCalls())
                        .failureRateThreshold(properties.getFailureRateThreshold())
                        .waitDurationInOpenState(properties.getWaitDurationInOpenState())
                        .permittedNumberOfCallsInHalfOpenState(
                                properties.getPermittedNumberOfCallsInHalfOpenState())
                        // A 4xx (422 "not waiting for payment", 404 unknown order) is a legitimate
                        // business answer, not a sign that order-service is unhealthy — it must not
                        // feed the failure rate.
                        .ignoreExceptions(FeignException.FeignClientException.class)
                        .build());
    }
}
