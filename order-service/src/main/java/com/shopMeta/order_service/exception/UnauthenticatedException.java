package com.shopMeta.order_service.exception;

/**
 * The presented token does not identify a user (no numeric subject), so nothing can be owned or
 * scoped. Surfaces as 401.
 */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
