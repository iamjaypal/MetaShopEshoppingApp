package com.shopMeta.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopMeta.payment_service.client.OrderClient;
import com.shopMeta.payment_service.client.dto.OrderView;
import com.shopMeta.payment_service.config.ResilienceProperties;
import com.shopMeta.payment_service.exception.BusinessRuleException;
import com.shopMeta.payment_service.exception.DownstreamServiceException;
import com.shopMeta.payment_service.exception.ResourceNotFoundException;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

/**
 * Verifies the resilience decoration of the order-service calls: every unreachable/timeout/
 * open-breaker outcome becomes {@link DownstreamServiceException} (503), transient failures are
 * retried before they are reported, and the business answers hidden inside Feign statuses are
 * unwrapped — the order 404 into {@link ResourceNotFoundException}, the callback 422 into
 * {@link BusinessRuleException}.
 */
@ExtendWith(MockitoExtension.class)
class DownstreamClientsTest {

    @Mock
    private OrderClient orderClient;

    private DownstreamClients downstreamClients;

    @BeforeEach
    void setUp() {

        ResilienceProperties properties = new ResilienceProperties();
        properties.setRetryMaxAttempts(3);
        properties.setRetryWaitDuration(Duration.ofMillis(1));

        Retry retry = Retry.of("downstream-test",
                io.github.resilience4j.retry.RetryConfig.<String>custom()
                        .maxAttempts(properties.getRetryMaxAttempts())
                        .waitDuration(Duration.ofMillis(1))
                        .retryOnException(DownstreamClientsTest::isRetryable)
                        .build());

        CircuitBreaker breaker = CircuitBreaker.of("order-test",
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .slidingWindowSize(properties.getSlidingWindowSize())
                        .minimumNumberOfCalls(properties.getMinimumNumberOfCalls())
                        .failureRateThreshold(properties.getFailureRateThreshold())
                        .build());

        downstreamClients = new DownstreamClients(orderClient, retry, breaker);
    }

    /** Same predicate as ResilienceConfig.isRetryable, kept local so tests stay hermetic. */
    private static boolean isRetryable(Throwable throwable) {
        if (throwable instanceof FeignException feignException) {
            return feignException.status() <= 0 || feignException.status() >= 500;
        }
        return throwable instanceof IllegalStateException;
    }

    private static FeignException feignError(Class<? extends FeignException> type, String body)
            throws Exception {

        Request request = Request.create(Request.HttpMethod.GET,
                "http://order-service/api/v1/internal/orders/100",
                Map.of(), body == null ? null : body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8, new RequestTemplate());

        return type.getDeclaredConstructor(String.class, Request.class, byte[].class, Map.class)
                .newInstance("downstream answered " + type.getSimpleName(), request,
                        body == null ? null : body.getBytes(StandardCharsets.UTF_8),
                        Map.of());
    }

    @Test
    @DisplayName("readOrder: returns the order's authoritative state")
    void readOrder_happyPath() {

        OrderView order = new OrderView();
        order.setId(100L);
        given(orderClient.getOrder(100L)).willReturn(order);

        assertThat(downstreamClients.readOrder(100L).getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("readOrder: a 404 (unknown or foreign order) becomes ResourceNotFoundException")
    void readOrder_maps404() throws Exception {

        given(orderClient.getOrder(anyLong()))
                .willThrow(feignError(FeignException.NotFound.class,
                        "{\"success\":false,\"message\":\"Order 100 was not found\"}"));

        assertThatThrownBy(() -> downstreamClients.readOrder(100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("100");
    }

    @Test
    @DisplayName("readOrder: an order-service outage is a 503 after the retries")
    void readOrder_outage() {

        given(orderClient.getOrder(anyLong()))
                .willThrow(new IllegalStateException("connection refused"));

        assertThatThrownBy(() -> downstreamClients.readOrder(100L))
                .isInstanceOf(DownstreamServiceException.class)
                .hasMessageContaining("order-service");

        then(orderClient).should(times(3)).getOrder(anyLong());
    }

    @Test
    @DisplayName("reportPaymentConfirmed: the 422 of a refused confirmation becomes a BusinessRuleException")
    void reportConfirmed_maps422() throws Exception {

        org.mockito.BDDMockito.willThrow(feignError(FeignException.UnprocessableEntity.class,
                "{\"success\":false,\"message\":\"The reserved stock for order 100 expired and "
                        + "the order cannot be confirmed\"}"))
                .given(orderClient).paymentConfirmed(anyLong());

        assertThatThrownBy(() -> downstreamClients.reportPaymentConfirmed(100L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");

        // A 422 is an answer, not an outage — it must not be retried.
        then(orderClient).should(times(1)).paymentConfirmed(anyLong());
    }

    @Test
    @DisplayName("reportPaymentFailed: a 422 (another replay already cancelled) is a BusinessRuleException")
    void reportFailed_maps422() throws Exception {

        org.mockito.BDDMockito.willThrow(feignError(FeignException.UnprocessableEntity.class,
                "{\"success\":false,\"message\":\"Order 100 is CANCELLED and cannot be failed\"}"))
                .given(orderClient).paymentFailed(anyLong());

        assertThatThrownBy(() -> downstreamClients.reportPaymentFailed(100L))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("a transient failure on a callback is retried — the callbacks are idempotent (D-071)")
    void transientCallbackFailure_isRetried() {

        // IllegalStateException is retryable by the shared policy; a checked exception cannot be
        // stubbed on a method that does not declare it.
        org.mockito.BDDMockito.willThrow(new IllegalStateException("connection reset"))
                .willDoNothing()
                .given(orderClient).paymentConfirmed(anyLong());

        downstreamClients.reportPaymentConfirmed(100L);

        then(orderClient).should(times(2)).paymentConfirmed(anyLong());
    }

    @Test
    @DisplayName("a failure that is not worth retrying is reported after one attempt")
    void nonRetryableFailure_singleAttempt() {

        given(orderClient.getOrder(anyLong())).willThrow(new NullPointerException("unexpected"));

        assertThatThrownBy(() -> downstreamClients.readOrder(100L))
                .isInstanceOf(DownstreamServiceException.class);

        then(orderClient).should(times(1)).getOrder(anyLong());
    }
}
