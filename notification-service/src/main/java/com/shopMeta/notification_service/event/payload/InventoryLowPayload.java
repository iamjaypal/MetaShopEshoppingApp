package com.shopMeta.notification_service.event.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code InventoryLowEvent} (spec 14.3) — triggers the low-stock alert.
 *
 * <p>There is no owning user here, so this one goes to the configured admin address rather than to a
 * customer (spec 14.1 calls it "Notification (admin low-stock)").
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryLowPayload(
        Long productId,
        String sku,
        Integer available) {
}
