package com.shopMeta.cart_service.exception;

/**
 * The caller could not be identified from their access token (no numeric subject). Returns 401.
 */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
