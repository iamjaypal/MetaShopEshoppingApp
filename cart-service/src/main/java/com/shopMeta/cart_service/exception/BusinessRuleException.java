package com.shopMeta.cart_service.exception;

/**
 * A request violates a business rule (e.g. the per-line quantity cap, a product that is no longer
 * available). Returns 422.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
