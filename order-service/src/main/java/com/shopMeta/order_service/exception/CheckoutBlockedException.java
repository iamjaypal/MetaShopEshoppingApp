package com.shopMeta.order_service.exception;

import java.util.List;

import com.shopMeta.order_service.client.dto.CartIssueView;

import lombok.Getter;

/**
 * cart-service's pre-checkout validation found a problem (a line out of stock, an inactive
 * product, over the quantity limit), so checkout is refused before any money or stock is
 * touched. Surfaces as 409 with the issues in {@code data}.
 */
@Getter
public class CheckoutBlockedException extends RuntimeException {

    private final transient List<CartIssueView> issues;

    public CheckoutBlockedException(String message, List<CartIssueView> issues) {
        super(message);
        this.issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
