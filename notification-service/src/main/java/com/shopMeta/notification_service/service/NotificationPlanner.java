package com.shopMeta.notification_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopMeta.notification_service.config.NotificationProperties;
import com.shopMeta.notification_service.entity.NotificationType;
import com.shopMeta.notification_service.event.EventEnvelope;
import com.shopMeta.notification_service.event.payload.InventoryLowPayload;
import com.shopMeta.notification_service.event.payload.OrderCancelledPayload;
import com.shopMeta.notification_service.event.payload.OrderConfirmedPayload;
import com.shopMeta.notification_service.event.payload.OrderStatusPayload;
import com.shopMeta.notification_service.event.payload.PasswordResetRequestedPayload;
import com.shopMeta.notification_service.event.payload.PaymentFailedPayload;
import com.shopMeta.notification_service.event.payload.PaymentRefundedPayload;
import com.shopMeta.notification_service.event.payload.PaymentSuccessPayload;
import com.shopMeta.notification_service.event.payload.UserRegisteredPayload;
import com.shopMeta.notification_service.exception.MalformedEventException;
import com.shopMeta.notification_service.mail.TemplateRenderer;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a domain event into the email it should produce (spec 13).
 *
 * <p>The mapping is a switch on {@code eventType} rather than a class hierarchy: there are ten cases,
 * they share nothing but the envelope, and a reader can see the whole policy in one screen. An event
 * type that maps to no email returns {@code null} and is skipped — that is how the consumer tolerates
 * the many events on these topics that are not interesting to it (OrderCreatedEvent, StockReserved…).
 *
 * <p>All money is formatted here, once, into the string the template prints. Templates then stay free
 * of locale and number-formatting concerns, and the rendering is deterministic in tests.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationPlanner {

    /** The currency every MetaShop price is quoted in (spec 11.1). */
    private static final Locale RUPEES = Locale.forLanguageTag("en-IN");

    private final TemplateRenderer renderer;

    private final ObjectMapper objectMapper;

    private final NotificationProperties properties;

    /**
     * @return the email to send, or {@code null} when this event does not produce one
     * @throws MalformedEventException when the payload does not match the event type — a permanent
     *                                 failure, so the record ends up on the dead-letter topic
     */
    public NotificationPlan plan(EventEnvelope envelope) {

        return switch (envelope.eventType()) {

            case EventEnvelope.Types.USER_REGISTERED -> welcome(envelope);

            case EventEnvelope.Types.PASSWORD_RESET_REQUESTED -> passwordReset(envelope);

            case EventEnvelope.Types.ORDER_CONFIRMED -> orderConfirmed(envelope);

            case EventEnvelope.Types.ORDER_SHIPPED -> orderShipped(envelope);

            case EventEnvelope.Types.ORDER_DELIVERED -> orderDelivered(envelope);

            case EventEnvelope.Types.ORDER_CANCELLED -> orderCancelled(envelope);

            case EventEnvelope.Types.PAYMENT_SUCCESS -> paymentSuccess(envelope);

            case EventEnvelope.Types.PAYMENT_FAILED -> paymentFailed(envelope);

            case EventEnvelope.Types.PAYMENT_REFUNDED -> paymentRefunded(envelope);

            case EventEnvelope.Types.INVENTORY_LOW -> inventoryLow(envelope);

            default -> {
                log.debug("Event type {} produces no notification; skipping", envelope.eventType());
                yield null;
            }
        };
    }

    // ------------------------------------------------------------------ user

    private NotificationPlan welcome(EventEnvelope envelope) {

        UserRegisteredPayload payload = read(envelope, UserRegisteredPayload.class);

        if (!hasText(payload.email())) {
            log.warn("{} {} carries no email address; skipping", envelope.eventType(), envelope.eventId());
            return null;
        }

        Map<String, Object> model = new HashMap<>();
        model.put("firstName", greetingName(payload.firstName(), payload.email()));
        model.put("email", payload.email());
        model.put("shopUrl", properties.getFrontendBaseUrl() + "/products");

        return new NotificationPlan(
                envelope.eventId(),
                payload.userId(),
                payload.email(),
                NotificationType.WELCOME,
                "Welcome to MetaShop",
                renderer.render("welcome", model));
    }

    private NotificationPlan passwordReset(EventEnvelope envelope) {

        PasswordResetRequestedPayload payload = read(envelope, PasswordResetRequestedPayload.class);

        if (!hasText(payload.email())) {
            log.warn("{} {} carries no email address; skipping", envelope.eventType(), envelope.eventId());
            return null;
        }

        Map<String, Object> model = new HashMap<>();
        model.put("greetingName", greetingName(null, payload.email()));
        model.put("resetUrl", properties.getFrontendBaseUrl()
                + "/reset-password?token=" + urlEncode(payload.resetToken()));

        return new NotificationPlan(
                envelope.eventId(),
                payload.userId(),
                payload.email(),
                NotificationType.PASSWORD_RESET,
                "Reset your MetaShop password",
                renderer.render("password-reset", model));
    }

    // ----------------------------------------------------------------- order

    private NotificationPlan orderConfirmed(EventEnvelope envelope) {

        OrderConfirmedPayload payload = read(envelope, OrderConfirmedPayload.class);

        if (!hasText(payload.email())) {
            log.warn("{} {} carries no email address; skipping", envelope.eventType(), envelope.eventId());
            return null;
        }

        Map<String, Object> model = new HashMap<>();
        model.put("greetingName", greetingName(null, payload.email()));
        model.put("orderNumber", payload.orderNumber());
        model.put("orderUrl", properties.getFrontendBaseUrl() + "/orders/" + payload.orderId());
        model.put("items", lineViews(payload.items()));
        model.put("total", money(payload.total()));
        model.put("shippingAddress", payload.shippingAddress());

        return new NotificationPlan(
                envelope.eventId(),
                payload.userId(),
                payload.email(),
                NotificationType.ORDER_CONFIRMED,
                "Order " + payload.orderNumber() + " confirmed",
                renderer.render("order-confirmed", model));
    }

    private NotificationPlan orderShipped(EventEnvelope envelope) {

        OrderStatusPayload payload = read(envelope, OrderStatusPayload.class);

        if (!hasText(payload.email())) {
            log.warn("{} {} carries no email address; skipping", envelope.eventType(), envelope.eventId());
            return null;
        }

        Map<String, Object> model = new HashMap<>();
        model.put("greetingName", greetingName(null, payload.email()));
        model.put("orderNumber", payload.orderNumber());
        model.put("orderUrl", properties.getFrontendBaseUrl() + "/orders/" + payload.orderId());

        return new NotificationPlan(
                envelope.eventId(),
                payload.userId(),
                payload.email(),
                NotificationType.ORDER_SHIPPED,
                "Order " + payload.orderNumber() + " is on its way",
                renderer.render("order-shipped", model));
    }

    private NotificationPlan orderDelivered(EventEnvelope envelope) {

        OrderStatusPayload payload = read(envelope, OrderStatusPayload.class);

        if (!hasText(payload.email())) {
            log.warn("{} {} carries no email address; skipping", envelope.eventType(), envelope.eventId());
            return null;
        }

        Map<String, Object> model = new HashMap<>();
        model.put("greetingName", greetingName(null, payload.email()));
        model.put("orderNumber", payload.orderNumber());
        model.put("orderUrl", properties.getFrontendBaseUrl() + "/orders/" + payload.orderId());

        return new NotificationPlan(
                envelope.eventId(),
                payload.userId(),
                payload.email(),
                NotificationType.ORDER_DELIVERED,
                "Order " + payload.orderNumber() + " delivered",
                renderer.render("order-delivered", model));
    }

    private NotificationPlan orderCancelled(EventEnvelope envelope) {

        OrderCancelledPayload payload = read(envelope, OrderCancelledPayload.class);

        if (!hasText(payload.email())) {
            log.warn("{} {} carries no email address; skipping", envelope.eventType(), envelope.eventId());
            return null;
        }

        Map<String, Object> model = new HashMap<>();
        model.put("greetingName", greetingName(null, payload.email()));
        model.put("orderNumber", payload.orderNumber());
        model.put("reason", hasText(payload.reason()) ? payload.reason() : "No reason was given");
        model.put("refundRequired", Boolean.TRUE.equals(payload.refundRequired()));

        return new NotificationPlan(
                envelope.eventId(),
                payload.userId(),
                payload.email(),
                NotificationType.ORDER_CANCELLED,
                "Order " + payload.orderNumber() + " cancelled",
                renderer.render("order-cancelled", model));
    }

    // --------------------------------------------------------------- payment

    private NotificationPlan paymentSuccess(EventEnvelope envelope) {

        PaymentSuccessPayload payload = read(envelope, PaymentSuccessPayload.class);

        if (!hasText(payload.email())) {
            log.warn("{} {} carries no email address, so no receipt can be sent. The producer must "
                    + "include the optional email field (D-081).", envelope.eventType(), envelope.eventId());
            return null;
        }

        Map<String, Object> model = new HashMap<>();
        model.put("greetingName", greetingName(null, payload.email()));
        model.put("amount", money(payload.amount()));
        model.put("method", hasText(payload.method()) ? payload.method() : "your payment method");
        model.put("orderId", payload.orderId());
        model.put("paymentId", payload.paymentId());
        model.put("orderUrl", properties.getFrontendBaseUrl() + "/orders/" + payload.orderId());

        return new NotificationPlan(
                envelope.eventId(),
                null,
                payload.email(),
                NotificationType.PAYMENT_SUCCESS,
                "Payment received",
                renderer.render("payment-success", model));
    }

    private NotificationPlan paymentFailed(EventEnvelope envelope) {

        PaymentFailedPayload payload = read(envelope, PaymentFailedPayload.class);

        if (!hasText(payload.email())) {
            log.warn("{} {} carries no email address, so no failure notice can be sent. The producer "
                    + "must include the optional email field (D-081).", envelope.eventType(), envelope.eventId());
            return null;
        }

        Map<String, Object> model = new HashMap<>();
        model.put("greetingName", greetingName(null, payload.email()));
        model.put("reason", hasText(payload.reason()) ? payload.reason() : "the payment was declined");
        model.put("orderId", payload.orderId());
        model.put("paymentId", payload.paymentId());
        model.put("checkoutUrl", properties.getFrontendBaseUrl() + "/checkout");

        return new NotificationPlan(
                envelope.eventId(),
                null,
                payload.email(),
                NotificationType.PAYMENT_FAILED,
                "We could not process your payment",
                renderer.render("payment-failed", model));
    }

    private NotificationPlan paymentRefunded(EventEnvelope envelope) {

        PaymentRefundedPayload payload = read(envelope, PaymentRefundedPayload.class);

        if (!hasText(payload.email())) {
            log.warn("{} {} carries no email address, so no refund notice can be sent. The producer "
                    + "must include the optional email field (D-081).", envelope.eventType(), envelope.eventId());
            return null;
        }

        Map<String, Object> model = new HashMap<>();
        model.put("greetingName", greetingName(null, payload.email()));
        model.put("amount", money(payload.amount()));
        model.put("orderId", payload.orderId());
        model.put("paymentId", payload.paymentId());

        return new NotificationPlan(
                envelope.eventId(),
                null,
                payload.email(),
                NotificationType.PAYMENT_REFUNDED,
                "Your refund has been issued",
                renderer.render("payment-refunded", model));
    }

    // ------------------------------------------------------------- inventory

    /**
     * The one notification with no customer behind it: an operational alert about stock. It goes to
     * the configured admin address (spec 14.1 "Notification (admin low-stock)").
     */
    private NotificationPlan inventoryLow(EventEnvelope envelope) {

        InventoryLowPayload payload = read(envelope, InventoryLowPayload.class);

        Map<String, Object> model = new HashMap<>();
        model.put("productId", payload.productId());
        model.put("sku", payload.sku());
        model.put("available", payload.available());

        return new NotificationPlan(
                envelope.eventId(),
                null,
                properties.getAdminEmail(),
                NotificationType.LOW_STOCK,
                "Low stock: " + (hasText(payload.sku()) ? payload.sku() : "product " + payload.productId()),
                renderer.render("low-stock", model));
    }

    // --------------------------------------------------------------- helpers

    /**
     * Maps the envelope's raw {@code payload} node onto the record for this event type.
     *
     * @throws MalformedEventException when the payload does not fit the expected shape
     */
    private <T> T read(EventEnvelope envelope, Class<T> type) {
        try {
            T payload = objectMapper.treeToValue(envelope.payload(), type);
            if (payload == null) {
                throw new MalformedEventException(
                        envelope.eventType() + " " + envelope.eventId() + " has no payload", null);
            }
            return payload;

        } catch (JsonProcessingException ex) {
            throw new MalformedEventException(
                    "Could not read the payload of " + envelope.eventType() + " "
                            + envelope.eventId() + ": " + ex.getOriginalMessage(), ex);
        }
    }

    /**
     * Formats the order lines for the confirmation email. The money is formatted here rather than in
     * the template so the rendered HTML is byte-identical for a given payload.
     */
    private List<OrderLineView> lineViews(List<OrderConfirmedPayload.OrderLine> lines) {

        if (lines == null) {
            return List.of();
        }

        List<OrderLineView> views = new ArrayList<>(lines.size());

        for (OrderConfirmedPayload.OrderLine line : lines) {
            views.add(new OrderLineView(
                    line.name(),
                    line.quantity() == null ? 0 : line.quantity(),
                    money(line.unitPrice()),
                    money(line.lineTotal())));
        }

        return views;
    }

    /** The name the email greets, falling back to the local part of the address. */
    private static String greetingName(String firstName, String email) {
        if (hasText(firstName)) {
            return firstName.trim();
        }
        if (hasText(email) && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        return "there";
    }

    private static String money(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        return NumberFormat.getCurrencyInstance(RUPEES).format(amount);
    }

    private static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    /** A pre-formatted order line, so the template only prints. */
    public record OrderLineView(String name, int quantity, String unitPrice, String lineTotal) {
    }
}
