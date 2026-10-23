package com.shopMeta.payment_service.exception;

/**
 * A resource the caller referred to does not exist (or exists but belongs to someone else, which
 * is deliberately reported the same way so ids cannot be probed — spec 18).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
