package com.shopMeta.notification_service.event.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * {@code PaymentRefundedEvent} (spec 14.3) — triggers the "refund issued" email.
 *
 * <p>See {@link PaymentSuccessPayload} for why {@code email} is an optional addition to the
 * catalogue's field list (D-081).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentRefundedPayload(
        Long paymentId,
        Long orderId,
        BigDecimal amount,
        String email) {
}
