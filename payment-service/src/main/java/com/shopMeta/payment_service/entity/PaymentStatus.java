package com.shopMeta.payment_service.entity;

/**
 * The state of one payment attempt (spec 12).
 *
 * <ul>
 *   <li>{@code PENDING} — the charge is being taken (row exists so an interrupted attempt is
 *       visible in the ledger).</li>
 *   <li>{@code SUCCEEDED} — the gateway captured the charge <em>and</em> order-service accepted the
 *       confirmation (its order is {@code CONFIRMED}).</li>
 *   <li>{@code FAILED} — the gateway declined, or order-service refused the confirmation (e.g. the
 *       stock reservation expired while the payment was in flight). The customer may retry; the
 *       retry is a new row.</li>
 * </ul>
 */
public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED
}
