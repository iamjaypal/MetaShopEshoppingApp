package com.shopMeta.order_service.entity;

/**
 * The payment state of the order, mirrored from payment-service (built in the next phase). Kept on
 * the order so the order detail alone tells the whole story.
 *
 * <ul>
 *   <li>{@code PENDING} — order created, stock reserved, waiting for the payment result.</li>
 *   <li>{@code PAID} — payment-service reported success (the order is {@code CONFIRMED}).</li>
 *   <li>{@code FAILED} — payment-service reported failure or the payment window expired
 *       (the order is {@code CANCELLED}). An order cancelled after payment keeps {@code PAID};
 *       the refund is payment-service's business.</li>
 * </ul>
 */
public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED
}
