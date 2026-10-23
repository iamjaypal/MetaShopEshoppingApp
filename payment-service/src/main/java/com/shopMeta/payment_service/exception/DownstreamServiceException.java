package com.shopMeta.payment_service.exception;

/**
 * order-service is unreachable, timed out, or its circuit breaker is open after the retry policy
 * (spec 5.8). Surfaces as an honest 503 — a payment is never attempted on a guess about the
 * order, and an unconfirmed charge is reported as exactly that.
 */
public class DownstreamServiceException extends RuntimeException {

    public DownstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
