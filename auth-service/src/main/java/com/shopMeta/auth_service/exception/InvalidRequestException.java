package com.shopMeta.auth_service.exception;

/**
 * A request parameter or query value is invalid (e.g. an unknown sort field or role filter).
 * Returns 400.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
