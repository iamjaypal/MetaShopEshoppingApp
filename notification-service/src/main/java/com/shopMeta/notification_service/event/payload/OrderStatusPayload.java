package com.shopMeta.notification_service.event.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code OrderShippedEvent} and {@code OrderDeliveredEvent} (spec 14.3). The catalogue lists them
 * with the same four fields, so one record serves both rather than two identical ones.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderStatusPayload(
        Long orderId,
        String orderNumber,
        Long userId,
        String email) {
}
