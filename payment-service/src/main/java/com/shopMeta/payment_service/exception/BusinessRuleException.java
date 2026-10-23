package com.shopMeta.payment_service.exception;

/**
 * A business rule refused the request: the order is not waiting for payment, it was already paid,
 * a charge is already in progress. Surfaces as 422.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
