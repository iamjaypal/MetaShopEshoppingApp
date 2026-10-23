package com.shopMeta.cart_service.exception;

import lombok.Getter;

/**
 * Adding or updating a line asked for more units than inventory-service reports as available.
 * The whole change is refused with 409 and {@code data} names the product with the real number,
 * so the storefront can show "only 2 left" next to the right line.
 */
@Getter
public class InsufficientStockException extends RuntimeException {

    private final transient Long productId;

    /** Exact sellable units inventory-service reported (never capped — the internal API). */
    private final int availableQuantity;

    public InsufficientStockException(String message, Long productId, int availableQuantity) {
        super(message);
        this.productId = productId;
        this.availableQuantity = availableQuantity;
    }
}
