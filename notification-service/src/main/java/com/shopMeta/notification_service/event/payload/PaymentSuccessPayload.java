package com.shopMeta.notification_service.event.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * {@code PaymentSuccessEvent} (spec 14.3) — triggers the "payment received" email.
 *
 * <p><strong>{@code email} is an addition to the catalogue's field list.</strong> The spec's payload
 * is {@code paymentId, orderId, amount, method} with no address, and nothing on the payment side can
 * currently resolve one: order-service's internal order read does not expose the buyer's email
 * either (see D-081). Since spec 14.4 explicitly allows adding optional, backward-compatible fields,
 * the producer is expected to include it. A payment event without an address is logged and skipped
 * rather than failing, so an older producer cannot poison the consumer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentSuccessPayload(
        Long paymentId,
        Long orderId,
        BigDecimal amount,
        String method,
        String email) {
}
