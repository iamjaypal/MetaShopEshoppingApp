package com.shopMeta.notification_service.exception;

/**
 * A request violates a business rule (e.g. retrying a notification that was already sent). Returns
 * 422 (spec 5.1).
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
