package com.shopMeta.inventory_service.entity;

/**
 * Reservation lifecycle (spec 9.4):
 * {@code RESERVED → CONFIRMED} on payment success, or {@code RESERVED → RELEASED} on payment
 * failure / cancellation, or {@code RESERVED → EXPIRED} when the sweeper times it out.
 */
public enum ReservationStatus {

    /** Stock is held for the order but not yet sold. */
    RESERVED,

    /** Payment succeeded — the held stock became sold (reservedQuantity decreased). */
    CONFIRMED,

    /** Payment failed or the order was cancelled — the stock went back to available. */
    RELEASED,

    /** The reservation was never confirmed in time and the sweeper released it. */
    EXPIRED

}
