package com.shopMeta.notification_service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopMeta.notification_service.config.NotificationProperties;
import com.shopMeta.notification_service.entity.NotificationType;
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
import com.shopMeta.notification_service.service.NotificationPlan;
import com.shopMeta.notification_service.service.NotificationPlanner;

import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The event-to-email mapping, rendered through the real Thymeleaf templates.
 *
 * <p>The templates are rendered rather than mocked on purpose: a typo in a template name or a
 * mistyped model attribute is exactly the class of bug that only shows up at send time, and this
 * test is what stops it reaching Mailpit.
 */
class NotificationPlannerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final NotificationProperties properties = new NotificationProperties();

    private final NotificationPlanner planner = new NotificationPlanner(
            new TemplateRenderer(templateEngine()), objectMapper, properties);

    // -------------------------------------------------------------- user

    @Test
    void userRegistered_rendersTheWelcomeEmail() {

        NotificationPlan plan = planner.plan(envelope(EventEnvelope.Types.USER_REGISTERED,
                new UserRegisteredPayload(7L, "asha@example.com", "Asha")));

        assertThat(plan.type()).isEqualTo(NotificationType.WELCOME);
        assertThat(plan.email()).isEqualTo("asha@example.com");
        assertThat(plan.userId()).isEqualTo(7L);
        assertThat(plan.subject()).isEqualTo("Welcome to MetaShop");
        assertThat(plan.body())
                .contains("Welcome aboard, Asha")
                .contains("asha@example.com")
                .contains("http://localhost:4200/products");
    }

    @Test
    void userRegistered_withoutAFirstName_greetsTheLocalPartOfTheAddress() {

        NotificationPlan plan = planner.plan(envelope(EventEnvelope.Types.USER_REGISTERED,
                new UserRegisteredPayload(7L, "asha@example.com", null)));

        assertThat(plan.body()).contains("Welcome aboard, asha");
    }

    @Test
    void passwordReset_putsTheTokenInTheLink() {

        NotificationPlan plan = planner.plan(envelope(EventEnvelope.Types.PASSWORD_RESET_REQUESTED,
                new PasswordResetRequestedPayload(7L, "asha@example.com", "tok en/+")));

        assertThat(plan.type()).isEqualTo(NotificationType.PASSWORD_RESET);
        assertThat(plan.subject()).isEqualTo("Reset your MetaShop password");
        assertThat(plan.body())
                .contains("http://localhost:4200/reset-password?token=tok+en%2F%2B")
                .contains("ignore this email");
    }

    // ------------------------------------------------------------- order

    @Test
    void orderConfirmed_includesTheLineItemsTheTotalAndTheShippingAddress() {

        OrderConfirmedPayload payload = new OrderConfirmedPayload(
                42L,
                "MS-000042",
                7L,
                "asha@example.com",
                List.of(
                        new OrderConfirmedPayload.OrderLine(1L, "SKU-1", "Wireless Mouse", null,
                                new BigDecimal("1299.00"), 2, new BigDecimal("2598.00"))),
                new BigDecimal("2598.00"),
                new OrderConfirmedPayload.ShippingAddress("Asha Rao", "9876543210", "12 MG Road",
                        "Flat 4B", "Bengaluru", "KA", "560001", "India"));

        NotificationPlan plan = planner.plan(
                envelope(EventEnvelope.Types.ORDER_CONFIRMED, payload));

        assertThat(plan.type()).isEqualTo(NotificationType.ORDER_CONFIRMED);
        assertThat(plan.subject()).isEqualTo("Order MS-000042 confirmed");
        assertThat(plan.body())
                .contains("Wireless Mouse")
                .contains(">2<")
                .contains("12 MG Road")
                .contains("Flat 4B")
                .contains("Bengaluru")
                .contains("560001")
                .contains("http://localhost:4200/orders/42");
    }

    @Test
    void orderConfirmed_withoutASecondAddressLine_doesNotPrintAnEmptyLine() {

        OrderConfirmedPayload payload = new OrderConfirmedPayload(
                42L, "MS-000042", 7L, "asha@example.com", List.of(),
                new BigDecimal("99.00"),
                new OrderConfirmedPayload.ShippingAddress("Asha Rao", null, "12 MG Road",
                        null, "Bengaluru", "KA", "560001", "India"));

        NotificationPlan plan = planner.plan(
                envelope(EventEnvelope.Types.ORDER_CONFIRMED, payload));

        assertThat(plan.body()).doesNotContain("null");
    }

    @Test
    void orderShipped_andOrderDelivered_shareTheSamePayloadShape() {

        OrderStatusPayload payload = new OrderStatusPayload(42L, "MS-000042", 7L, "asha@example.com");

        NotificationPlan shipped =
                planner.plan(envelope(EventEnvelope.Types.ORDER_SHIPPED, payload));
        NotificationPlan delivered =
                planner.plan(envelope(EventEnvelope.Types.ORDER_DELIVERED, payload));

        assertThat(shipped.type()).isEqualTo(NotificationType.ORDER_SHIPPED);
        assertThat(shipped.subject()).isEqualTo("Order MS-000042 is on its way");

        assertThat(delivered.type()).isEqualTo(NotificationType.ORDER_DELIVERED);
        assertThat(delivered.subject()).isEqualTo("Order MS-000042 delivered");
    }

    @Test
    void orderCancelled_saysWhetherMoneyIsComingBack() {

        NotificationPlan refunding = planner.plan(envelope(EventEnvelope.Types.ORDER_CANCELLED,
                new OrderCancelledPayload(42L, "MS-000042", 7L, "asha@example.com",
                        "Payment declined", true)));

        assertThat(refunding.body())
                .contains("A refund is on its way")
                .contains("Payment declined");

        NotificationPlan notRefunding = planner.plan(envelope(EventEnvelope.Types.ORDER_CANCELLED,
                new OrderCancelledPayload(42L, "MS-000042", 7L, "asha@example.com", null, false)));

        assertThat(notRefunding.body())
                .contains("nothing to refund")
                .contains("No reason was given");
    }

    // ----------------------------------------------------------- payment

    @Test
    void paymentSuccess_rendersAReceipt() {

        NotificationPlan plan = planner.plan(envelope(EventEnvelope.Types.PAYMENT_SUCCESS,
                new PaymentSuccessPayload(9L, 42L, new BigDecimal("2598.00"), "CARD",
                        "asha@example.com")));

        assertThat(plan.type()).isEqualTo(NotificationType.PAYMENT_SUCCESS);
        assertThat(plan.subject()).isEqualTo("Payment received");
        assertThat(plan.body())
                .contains("Paid")
                .contains("CARD")
                .contains("http://localhost:4200/orders/42");
    }

    @Test
    void paymentFailed_tellsTheCustomerNothingWasCharged() {

        NotificationPlan plan = planner.plan(envelope(EventEnvelope.Types.PAYMENT_FAILED,
                new PaymentFailedPayload(9L, 42L, "Insufficient funds", "asha@example.com")));

        assertThat(plan.type()).isEqualTo(NotificationType.PAYMENT_FAILED);
        assertThat(plan.body())
                .contains("Nothing has been charged")
                .contains("Insufficient funds")
                .contains("http://localhost:4200/checkout");
    }

    @Test
    void paymentRefunded_rendersTheAmount() {

        NotificationPlan plan = planner.plan(envelope(EventEnvelope.Types.PAYMENT_REFUNDED,
                new PaymentRefundedPayload(9L, 42L, new BigDecimal("2598.00"),
                        "asha@example.com")));

        assertThat(plan.type()).isEqualTo(NotificationType.PAYMENT_REFUNDED);
        assertThat(plan.subject()).isEqualTo("Your refund has been issued");
        assertThat(plan.body()).contains("Refunded");
    }

    /**
     * Documents the one hole the event catalogue leaves (D-081): a payment event carries no address,
     * and nothing on the payment side can currently resolve one. The consumer must skip such an event
     * rather than fail on it, so that an older producer cannot poison the topic.
     */
    @Test
    void paymentEventsWithoutAnAddress_areSkippedRatherThanFailing() {

        assertThat(planner.plan(envelope(EventEnvelope.Types.PAYMENT_SUCCESS,
                new PaymentSuccessPayload(9L, 42L, new BigDecimal("10.00"), "CARD", null))))
                .isNull();

        assertThat(planner.plan(envelope(EventEnvelope.Types.PAYMENT_FAILED,
                new PaymentFailedPayload(9L, 42L, "declined", " "))))
                .isNull();
    }

    // --------------------------------------------------------- inventory

    @Test
    void inventoryLow_goesToTheAdminAddress() {

        properties.setAdminEmail("ops@metashop.com");

        NotificationPlan plan = planner.plan(envelope(EventEnvelope.Types.INVENTORY_LOW,
                new InventoryLowPayload(1L, "SKU-1", 2)));

        assertThat(plan.type()).isEqualTo(NotificationType.LOW_STOCK);
        assertThat(plan.email()).isEqualTo("ops@metashop.com");
        assertThat(plan.userId()).isNull();
        assertThat(plan.subject()).isEqualTo("Low stock: SKU-1");
        assertThat(plan.body()).contains("Operational alert").contains("SKU-1");
    }

    // ----------------------------------------------------------- skipping

    @Test
    void anEventTypeWithNoNotification_isSkipped() {

        assertThat(planner.plan(envelope("OrderCreatedEvent", java.util.Map.of("orderId", 42))))
                .isNull();
    }

    @Test
    void aPayloadThatDoesNotFitTheEventType_isAMalformedEvent() {

        // orderId is a number in the contract; a producer that sends something else is a bug that
        // must reach the dead-letter topic rather than be silently skipped.
        JsonNode wrong = objectMapper.valueToTree(java.util.Map.of("orderId", "not-a-number"));

        EventEnvelope envelope = new EventEnvelope("evt-1", EventEnvelope.Types.ORDER_CONFIRMED,
                OffsetDateTime.now(), 1, null, wrong);

        assertThatThrownBy(() -> planner.plan(envelope))
                .isInstanceOf(MalformedEventException.class)
                .hasMessageContaining("OrderConfirmedEvent");
    }

    /**
     * An envelope whose payload is missing entirely (a producer bug, or a null body) must not be
     * treated as "this event has no notification" — that would drop it silently.
     */
    @Test
    void aMissingPayload_isAMalformedEvent() {

        EventEnvelope envelope = new EventEnvelope("evt-1", EventEnvelope.Types.ORDER_SHIPPED,
                OffsetDateTime.now(), 1, null, null);

        assertThatThrownBy(() -> planner.plan(envelope))
                .isInstanceOf(MalformedEventException.class);
    }

    @Test
    void everyKnownEventTypeRendersWithoutError() {

        for (EventEnvelope envelope : List.of(
                envelope(EventEnvelope.Types.USER_REGISTERED,
                        new UserRegisteredPayload(1L, "a@b.com", "A")),
                envelope(EventEnvelope.Types.PASSWORD_RESET_REQUESTED,
                        new PasswordResetRequestedPayload(1L, "a@b.com", "t")),
                envelope(EventEnvelope.Types.ORDER_CONFIRMED,
                        new OrderConfirmedPayload(1L, "MS-1", 1L, "a@b.com", List.of(),
                                BigDecimal.ONE,
                                new OrderConfirmedPayload.ShippingAddress("A", null, "L1", null,
                                        "C", "S", "1", "IN"))),
                envelope(EventEnvelope.Types.ORDER_SHIPPED,
                        new OrderStatusPayload(1L, "MS-1", 1L, "a@b.com")),
                envelope(EventEnvelope.Types.ORDER_DELIVERED,
                        new OrderStatusPayload(1L, "MS-1", 1L, "a@b.com")),
                envelope(EventEnvelope.Types.ORDER_CANCELLED,
                        new OrderCancelledPayload(1L, "MS-1", 1L, "a@b.com", "why", true)),
                envelope(EventEnvelope.Types.PAYMENT_SUCCESS,
                        new PaymentSuccessPayload(1L, 1L, BigDecimal.ONE, "CARD", "a@b.com")),
                envelope(EventEnvelope.Types.PAYMENT_FAILED,
                        new PaymentFailedPayload(1L, 1L, "why", "a@b.com")),
                envelope(EventEnvelope.Types.PAYMENT_REFUNDED,
                        new PaymentRefundedPayload(1L, 1L, BigDecimal.ONE, "a@b.com")),
                envelope(EventEnvelope.Types.INVENTORY_LOW,
                        new InventoryLowPayload(1L, "SKU-1", 0)))) {

            NotificationPlan plan = planner.plan(envelope);

            assertThat(plan).as("plan for %s", envelope.eventType()).isNotNull();
            assertThat(plan.body()).as("body for %s", envelope.eventType())
                    .contains("MetaShop")
                    .contains("</html>");
        }
    }

    // ------------------------------------------------------------ helpers

    private EventEnvelope envelope(String eventType, Object payload) {
        return new EventEnvelope("evt-1", eventType, OffsetDateTime.now(), 1, null,
                objectMapper.valueToTree(payload));
    }

    /**
     * A standalone Thymeleaf engine over {@code src/main/resources/templates}, so the real templates
     * are exercised without starting a Spring context. {@code SpringTemplateEngine} rather than the
     * plain {@code TemplateEngine} because that is what Boot autoconfigures: it evaluates
     * {@code ${...}} with SpringEL and needs no OGNL, which the starter does not ship.
     */
    private static SpringTemplateEngine templateEngine() {

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        return engine;
    }
}
