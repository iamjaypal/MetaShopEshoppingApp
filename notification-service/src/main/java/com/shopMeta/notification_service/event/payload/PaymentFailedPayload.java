package com.shopMeta.notification_service.event.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code PaymentFailedEvent} (spec 14.3) — triggers the "payment failed" email.
 *
 * <p>See {@link PaymentSuccessPayload} for why {@code email} is an optional addition to the
 * catalogue's field list (D-081).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentFailedPayload(
        Long paymentId,
        Long orderId,
        String reason,
        String email) {
}
