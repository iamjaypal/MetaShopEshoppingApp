package com.shopMeta.auth_service.exception;

/**
 * A requested resource does not exist (or is not visible to the caller). Returns 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
