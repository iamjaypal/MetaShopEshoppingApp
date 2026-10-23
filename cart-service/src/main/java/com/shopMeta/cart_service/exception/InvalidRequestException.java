package com.shopMeta.cart_service.exception;

/**
 * A request parameter or query value is invalid. Returns 400.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
