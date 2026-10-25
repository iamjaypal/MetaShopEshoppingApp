package com.shopMeta.notification_service.event.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code OrderCancelledEvent} (spec 14.3) — triggers the cancellation email, which also has to say
 * whether money is coming back.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCancelledPayload(
        Long orderId,
        String orderNumber,
        Long userId,
        String email,
        String reason,
        Boolean refundRequired) {
}
