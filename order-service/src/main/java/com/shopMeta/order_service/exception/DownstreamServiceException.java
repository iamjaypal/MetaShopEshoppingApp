package com.shopMeta.order_service.exception;

/**
 * A downstream service (cart-, product-, inventory- or auth-service) is unreachable, timed out, or
 * its circuit breaker is open after the retry policy (spec 5.8). Surfaces as an honest 503 — an
 * order is never created on guessed data (spec 19).
 */
public class DownstreamServiceException extends RuntimeException {

    public DownstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
