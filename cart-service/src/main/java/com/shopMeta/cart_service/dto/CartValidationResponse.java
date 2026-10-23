package com.shopMeta.cart_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Answer of {@code POST /api/v1/cart/validate} (spec 10.3): "would this cart check out right now?".
 *
 * <p>Always 200 — an invalid cart is a normal answer to a question, not an error. Order-service
 * performs the authoritative check again when it reserves stock.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartValidationResponse {

    private boolean valid;

    /** Number of distinct products checked. */
    private int itemCount;

    private List<CartIssue> issues;

    private LocalDateTime checkedAt;
}
