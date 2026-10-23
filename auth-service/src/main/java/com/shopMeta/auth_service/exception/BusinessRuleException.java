package com.shopMeta.auth_service.exception;

/**
 * A request violates a business rule (e.g. more than 10 addresses, removing the default
 * address, wrong current password). Returns 422 (spec 5.1).
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
