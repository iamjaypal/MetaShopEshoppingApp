package com.shopMeta.order_service.entity;

/**
 * The order lifecycle (spec 11). Order matters: every transition other than the ones below is a
 * business-rule violation (422).
 *
 * <pre>
 * CREATED --(payment succeeded)--> CONFIRMED --(admin ships)--> SHIPPED --(admin delivers)--> DELIVERED
 *    |---(payment failed / user cancels)--> CANCELLED     CONFIRMED --(admin cancels)--> CANCELLED
 * </pre>
 *
 * {@code CANCELLED} and {@code DELIVERED} are terminal.
 */
public enum OrderStatus {
    CREATED,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
