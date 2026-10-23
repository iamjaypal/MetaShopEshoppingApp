package com.shopMeta.payment_service.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Retry and circuit-breaker tuning for the outbound calls to order-service (spec 5.8).
 *
 * <p>The timeout is not here: it is enforced by the Feign HTTP client
 * ({@code spring.cloud.openfeign.client.config.default.read-timeout}), because Resilience4j's
 * {@code TimeLimiter} only applies to asynchronous calls and this service is blocking MVC.
 */
@Component
@ConfigurationProperties(prefix = "metashop.resilience")
@Getter
@Setter
public class ResilienceProperties {

    /** Total attempts, including the first one (spec 5.8: 3). */
    private int retryMaxAttempts = 3;

    /** Delay before the first retry. */
    private Duration retryWaitDuration = Duration.ofMillis(200);

    /** Each subsequent retry waits this many times longer (exponential backoff). */
    private double retryBackoffMultiplier = 2.0;

    private int slidingWindowSize = 10;

    /** Stay CLOSED until this many calls have been recorded. */
    private int minimumNumberOfCalls = 5;

    /** Percentage of failed calls that trips the breaker to OPEN. */
    private float failureRateThreshold = 50f;

    /** How long the breaker stays OPEN before allowing probe calls again. */
    private Duration waitDurationInOpenState = Duration.ofSeconds(15);

    /** Probe calls allowed while HALF_OPEN. */
    private int permittedNumberOfCallsInHalfOpenState = 3;
}
