package com.shopMeta.order_service.exception;

/**
 * A business rule refused the request: an empty cart, an order lifecycle that cannot move that
 * way, a product that is no longer available. Surfaces as 422.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
