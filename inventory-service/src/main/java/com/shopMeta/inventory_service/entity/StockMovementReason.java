package com.shopMeta.inventory_service.entity;

/**
 * Why a stock row changed (spec 9.2). Stored in {@code stock_movements.reason}.
 */
public enum StockMovementReason {

    /** Admin created the stock row with an opening quantity. */
    INITIAL,

    /** Admin set/adjusted the quantity by hand. */
    ADMIN_ADJUSTMENT,

    /** Stock was held for an order ({@code availableQuantity -}, {@code reservedQuantity +}). */
    RESERVE,

    /** A hold was returned ({@code availableQuantity +}, {@code reservedQuantity -}). */
    RELEASE,

    /** A hold became a sale ({@code reservedQuantity -}). */
    CONFIRM,

    /** A stale hold was returned by the expiry sweeper. */
    EXPIRE

}
