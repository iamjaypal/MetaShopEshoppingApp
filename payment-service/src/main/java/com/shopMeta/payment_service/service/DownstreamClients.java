package com.shopMeta.payment_service.service;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.shopMeta.payment_service.client.OrderClient;
import com.shopMeta.payment_service.client.dto.OrderView;
import com.shopMeta.payment_service.config.ResilienceConfig;
import com.shopMeta.payment_service.exception.BusinessRuleException;
import com.shopMeta.payment_service.exception.DownstreamServiceException;
import com.shopMeta.payment_service.exception.ResourceNotFoundException;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

/**
 * The single place where payment-service talks to order-service (spec 5.8).
 *
 * <p>The call goes through the shared retry policy and the order-service circuit breaker. When
 * order-service is unreachable the call becomes a {@link DownstreamServiceException} — a clear 503.
 *
 * <p>Two business answers are unwrapped from their Feign status and rethrown typed, because they
 * are <em>answers</em>, not failures:
 *
 * <ul>
 *   <li>order read → 404 becomes {@link ResourceNotFoundException}.</li>
 *   <li>callbacks → 422 becomes {@link BusinessRuleException} (the order is no longer waiting for
 *       payment — e.g. its stock reservation expired, or another attempt won the race).</li>
 * </ul>
 */
@Service
public class DownstreamClients {

    private static final Logger log = LoggerFactory.getLogger(DownstreamClients.class);

    private final OrderClient orderClient;
    private final Retry retry;
    private final CircuitBreaker orderCircuitBreaker;

    public DownstreamClients(
            OrderClient orderClient,
            Retry downstreamRetry,
            @Qualifier("orderServiceCircuitBreaker") CircuitBreaker orderCircuitBreaker) {

        this.orderClient = orderClient;
        this.retry = downstreamRetry;
        this.orderCircuitBreaker = orderCircuitBreaker;
    }

    /**
     * The authoritative order state (owner, status, total). A 404 — unknown id, or an order that
     * belongs to someone else (order-service does not distinguish) — becomes
     * {@link ResourceNotFoundException}.
     */
    public OrderView readOrder(Long orderId) {

        try {
            return call(() -> orderClient.getOrder(orderId));

        } catch (DownstreamServiceException ex) {
            if (ex.getCause() instanceof FeignException.NotFound) {
                throw new ResourceNotFoundException("Order " + orderId + " was not found");
            }
            throw ex;
        }
    }

    /** Reports a captured charge (idempotent on the order-service side, D-071). */
    public void reportPaymentConfirmed(Long orderId) {

        try {
            call(() -> {
                orderClient.paymentConfirmed(orderId);
                return null;
            });
        } catch (DownstreamServiceException ex) {
            if (ex.getCause() instanceof FeignException feign && feign.status() == 422) {
                throw new BusinessRuleException(businessMessage(feign, orderId,
                        "Order " + orderId + " is no longer waiting for payment, so the charge "
                                + "cannot be applied"));
            }
            throw ex;
        }
    }

    /** Reports a declined charge; order-service cancels the order and releases the stock. */
    public void reportPaymentFailed(Long orderId) {

        try {
            call(() -> {
                orderClient.paymentFailed(orderId);
                return null;
            });
        } catch (DownstreamServiceException ex) {
            if (ex.getCause() instanceof FeignException feign && feign.status() == 422) {
                throw new BusinessRuleException(businessMessage(feign, orderId,
                        "Order " + orderId + " is no longer waiting for payment"));
            }
            throw ex;
        }
    }

    /**
     * Runs a downstream call through {@code Retry(CircuitBreaker(call))}.
     *
     * <p>The retry is outermost on purpose: a transient failure is retried before the breaker
     * counts it, so a single blip cannot open the circuit. Once the breaker is open the call fails
     * fast instead of adding load to a service that is already struggling. 4xx answers are not
     * retried (the request is wrong, not the service).
     */
    private <T> T call(Supplier<T> request) {

        Supplier<T> guarded = Retry.decorateSupplier(
                retry, CircuitBreaker.decorateSupplier(orderCircuitBreaker, request));

        try {
            return guarded.get();
        } catch (CallNotPermittedException ex) {
            log.warn("{} circuit breaker is open; failing fast", ResilienceConfig.ORDER_SERVICE);
            throw new DownstreamServiceException(
                    ResilienceConfig.ORDER_SERVICE
                            + " is temporarily unavailable. Please try again shortly.",
                    ex);
        } catch (RuntimeException ex) {
            // FeignException (bad status), RetryableException (connection refused/timeout) and
            // LoadBalancer's "no instances" all land here once the retries are exhausted.
            log.error("{} call failed: {}", ResilienceConfig.ORDER_SERVICE, ex.getMessage());
            throw new DownstreamServiceException(
                    "Could not reach " + ResilienceConfig.ORDER_SERVICE
                            + ". Please try again shortly.",
                    ex);
        }
    }

    /** Reads the {@code message} field of order-service's error envelope, with a fallback. */
    private String businessMessage(FeignException feignException, Long orderId, String fallback) {

        try {
            var root = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(feignException.contentUTF8());
            String message = root.path("message").asText("");
            return message.isBlank() ? fallback : message;

        } catch (Exception parseFailure) {
            return fallback;
        }
    }
}
