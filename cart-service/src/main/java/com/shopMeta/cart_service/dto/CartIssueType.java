package com.shopMeta.cart_service.dto;

/**
 * Why a cart line would stop the checkout (spec 10.3 {@code POST /api/v1/cart/validate}).
 */
public enum CartIssueType {

    /** Nothing to check out. */
    CART_EMPTY,

    /** The product no longer exists in the catalogue. */
    PRODUCT_NOT_FOUND,

    /** The product was deactivated after it was added. */
    PRODUCT_INACTIVE,

    /** Stock has run out completely. */
    OUT_OF_STOCK,

    /** Some stock is left, but not as much as the line asks for. */
    INSUFFICIENT_STOCK,

    /** The line is above the per-item ceiling, e.g. it was added before the limit changed. */
    QUANTITY_LIMIT_EXCEEDED
}
