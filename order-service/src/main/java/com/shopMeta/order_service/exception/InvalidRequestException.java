package com.shopMeta.order_service.exception;

/**
 * The request body is malformed in a way bean validation cannot express. Surfaces as 400.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
