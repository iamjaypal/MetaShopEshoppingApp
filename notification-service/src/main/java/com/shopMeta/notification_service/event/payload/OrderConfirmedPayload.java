package com.shopMeta.notification_service.event.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code OrderConfirmedEvent} (spec 14.3) — triggers the order confirmation email, the one email that
 * needs the full line-item table and the shipping address.
 *
 * <p>The nested {@code OrderLine} and {@code ShippingAddress} mirror order-service's
 * {@code OrderItemResponse} / {@code OrderAddressResponse}, which are the snapshots frozen onto the
 * order at checkout. The address is rendered from the order, never re-read from the address book, so
 * the email always shows where the parcel was actually sent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderConfirmedPayload(
        Long orderId,
        String orderNumber,
        Long userId,
        String email,
        List<OrderLine> items,
        BigDecimal total,
        ShippingAddress shippingAddress) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderLine(
            Long productId,
            String sku,
            String name,
            String imageUrl,
            BigDecimal unitPrice,
            Integer quantity,
            BigDecimal lineTotal) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShippingAddress(
            String fullName,
            String phone,
            String line1,
            String line2,
            String city,
            String state,
            String postalCode,
            String country) {
    }
}
