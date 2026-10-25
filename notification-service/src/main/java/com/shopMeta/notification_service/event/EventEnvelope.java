package com.shopMeta.notification_service.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

/**
 * The platform-wide event envelope (spec 14.2). Every domain event on every topic uses this shape,
 * so one listener can consume all four topics this service subscribes to.
 *
 * <p>The {@code payload} stays a {@link JsonNode} here and is mapped to a typed record per
 * {@code eventType} by {@link com.shopMeta.notification_service.service.NotificationPlanner}. That
 * keeps the deserializer simple and means an unknown or future event type is a logged skip rather
 * than a poison pill that stops the consumer.
 *
 * <p>{@code ignoreUnknown} on purpose: spec 14.4 requires producers to be able to add optional
 * fields without breaking existing consumers.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope(
        String eventId,
        String eventType,
        OffsetDateTime occurredAt,
        Integer version,
        String correlationId,
        JsonNode payload) {

    /** The event type constants this service knows how to turn into an email. */
    public static final class Types {

        public static final String USER_REGISTERED = "UserRegisteredEvent";
        public static final String PASSWORD_RESET_REQUESTED = "PasswordResetRequestedEvent";

        public static final String ORDER_CONFIRMED = "OrderConfirmedEvent";
        public static final String ORDER_SHIPPED = "OrderShippedEvent";
        public static final String ORDER_DELIVERED = "OrderDeliveredEvent";
        public static final String ORDER_CANCELLED = "OrderCancelledEvent";

        public static final String PAYMENT_SUCCESS = "PaymentSuccessEvent";
        public static final String PAYMENT_FAILED = "PaymentFailedEvent";
        public static final String PAYMENT_REFUNDED = "PaymentRefundedEvent";

        public static final String INVENTORY_LOW = "InventoryLowEvent";

        private Types() {
        }
    }
}
