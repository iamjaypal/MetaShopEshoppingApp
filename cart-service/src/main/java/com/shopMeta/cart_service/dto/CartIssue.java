package com.shopMeta.cart_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One reason a cart cannot be checked out.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartIssue {

    /** Null for cart-level issues such as {@code CART_EMPTY}. */
    private Long itemId;

    private Long productId;

    private CartIssueType type;

    private String message;
}
