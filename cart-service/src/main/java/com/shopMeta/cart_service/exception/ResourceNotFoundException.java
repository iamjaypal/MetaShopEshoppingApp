package com.shopMeta.cart_service.exception;

/**
 * A requested resource does not exist. Returns 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
