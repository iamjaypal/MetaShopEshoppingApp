package com.shopMeta.inventory_service.exception;

/**
 * A request parameter or query value is invalid (e.g. an unknown movement reason). Returns 400.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
