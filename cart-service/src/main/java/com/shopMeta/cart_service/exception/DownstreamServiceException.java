package com.shopMeta.cart_service.exception;

/**
 * product-service or inventory-service could not be reached after the retry policy was exhausted,
 * or its circuit breaker is open. Returns 503 (spec 5.1 / 5.8 / 19).
 *
 * <p>The cart is deliberately <em>not</em> rendered from stale or invented data when the catalogue
 * is unreachable: a wrong price is worse than an honest "try again shortly" (spec 10.2 / 19).
 */
public class DownstreamServiceException extends RuntimeException {

    public DownstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
