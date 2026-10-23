package com.shopMeta.inventory_service.exception;

/**
 * A request violates a business rule (e.g. stock already exists for the product, releasing an
 * order that was never reserved). Returns 422 (spec 5.1).
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
