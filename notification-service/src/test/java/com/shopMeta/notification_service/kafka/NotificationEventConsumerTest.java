package com.shopMeta.notification_service.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopMeta.notification_service.entity.Notification;
import com.shopMeta.notification_service.entity.NotificationStatus;
import com.shopMeta.notification_service.entity.NotificationType;
import com.shopMeta.notification_service.event.EventEnvelope;
import com.shopMeta.notification_service.event.KafkaTopics;
import com.shopMeta.notification_service.event.payload.OrderConfirmedPayload;
import com.shopMeta.notification_service.event.payload.PaymentSuccessPayload;
import com.shopMeta.notification_service.exception.NotificationDeliveryException;
import com.shopMeta.notification_service.mail.MailNotificationSender;
import com.shopMeta.notification_service.repository.NotificationRepository;
import com.shopMeta.notification_service.repository.ProcessedEventRepository;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * The consumer path against a real broker: publish an event, and check that an email was sent, a row
 * was written and the event was retired.
 *
 * <p>The broker is embedded rather than mocked because most of what can go wrong here lives in the
 * wiring — deserialization, the consumer group, the manual ack, the retry/back-off, the dead-letter
 * routing — and a mocked listener would test none of it.
 *
 * <p>The mail sender is mocked so no SMTP server is needed; that the HTML itself is correct is
 * {@code NotificationPlannerTest}'s job.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        // Shrink the retry policy so the dead-letter test does not sit through 1s + 2s + 4s.
        "metashop.notification.retry-max-attempts=2",
        "metashop.notification.retry-initial-interval=50ms",
        "metashop.notification.retry-multiplier=1.0",
        "metashop.notification.retry-max-interval=50ms"
})
@EmbeddedKafka(partitions = 1, topics = {
        KafkaTopics.USER_EVENTS,
        KafkaTopics.ORDER_EVENTS,
        KafkaTopics.PAYMENT_EVENTS,
        KafkaTopics.INVENTORY_EVENTS,
        KafkaTopics.ORDER_EVENTS + ".DLT"
})
class NotificationEventConsumerTest {

    private static final String CUSTOMER_EMAIL = "asha@example.com";

    private static final Duration AWAIT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private org.springframework.kafka.test.EmbeddedKafkaBroker broker;

    @MockitoBean
    private MailNotificationSender mailSender;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clearTheNotificationLog() {
        notificationRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    @Test
    void anOrderConfirmedEvent_isEmailedAndRecordedAsSent() throws Exception {

        EventEnvelope envelope = orderConfirmedEvent();

        publish(KafkaTopics.ORDER_EVENTS, envelope);

        Notification saved = awaitNotification(envelope.eventId());

        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_CONFIRMED);
        assertThat(saved.getEmail()).isEqualTo(CUSTOMER_EMAIL);
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getSubject()).isEqualTo("Order MS-000042 confirmed");
        assertThat(saved.getBody()).contains("Wireless Mouse");
        assertThat(saved.getSentAt()).isNotNull();
        assertThat(saved.getRetryCount()).isZero();

        // The event is retired, so a redelivery would be skipped.
        assertThat(processedEventRepository.existsById(envelope.eventId())).isTrue();

        then(mailSender).should().send(
                org.mockito.ArgumentMatchers.eq(CUSTOMER_EMAIL),
                org.mockito.ArgumentMatchers.eq("Order MS-000042 confirmed"),
                anyString());
    }

    @Test
    void aRedeliveredEvent_isNotEmailedTwice() throws Exception {

        EventEnvelope envelope = orderConfirmedEvent();

        publish(KafkaTopics.ORDER_EVENTS, envelope);
        awaitNotification(envelope.eventId());

        // Same event id again — this is what at-least-once delivery looks like.
        publish(KafkaTopics.ORDER_EVENTS, envelope);

        // Wait for the duplicate to have been seen (it is skipped, so nothing new appears) and then
        // confirm the second copy did not produce a second email or a second row.
        awaitProcessedEventCount(1);
        Thread.sleep(1_000);

        assertThat(notificationRepository.findByEventId(envelope.eventId())).isPresent();
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.count()).isEqualTo(1);

        then(mailSender).should(org.mockito.Mockito.times(1))
                .send(anyString(), anyString(), anyString());
    }

    @Test
    void anEventTypeWithNoNotification_isAcknowledgedAndStoredNowhere() throws Exception {

        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID().toString(), "OrderCreatedEvent",
                OffsetDateTime.now(), 1, null,
                objectMapper.valueToTree(Map.of("orderId", 42, "orderNumber", "MS-000042")));

        publish(KafkaTopics.ORDER_EVENTS, envelope);

        // Give the consumer time to have seen it; nothing should appear.
        Thread.sleep(2_000);

        assertThat(notificationRepository.count()).isZero();
        then(mailSender).shouldHaveNoInteractions();
    }

    /**
     * The one hole the event catalogue leaves (D-081): a payment event carries no address. The
     * consumer must skip it rather than fail, so an older producer cannot poison the topic.
     */
    @Test
    void aPaymentEventWithoutAnAddress_isSkippedRatherThanDeadLettered() throws Exception {

        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID().toString(),
                EventEnvelope.Types.PAYMENT_SUCCESS, OffsetDateTime.now(), 1, null,
                objectMapper.valueToTree(new PaymentSuccessPayload(
                        9L, 42L, new BigDecimal("2598.00"), "CARD", null)));

        publish(KafkaTopics.PAYMENT_EVENTS, envelope);

        Thread.sleep(2_000);

        assertThat(notificationRepository.count()).isZero();
        assertThat(drainDlt(KafkaTopics.PAYMENT_EVENTS + ".DLT",
                record -> record.value() != null && record.value().contains(envelope.eventId())))
                .isEmpty();
    }

    @Test
    void anUnparseableRecord_isDeadLetteredAndDoesNotStallTheConsumer() throws Exception {

        // Not valid JSON at all. Before the deserializers were wrapped in ErrorHandlingDeserializer
        // this froze the listener for ever: the container threw a RecordDeserializationException on
        // every poll and DefaultErrorHandler refused to process it.
        String key = "poison-" + UUID.randomUUID();
        sendRaw(KafkaTopics.ORDER_EVENTS, key, "{\"eventId\":\"poison\",\"eventType\":\"OrderConfirmedEvent\",");

        assertThat(awaitDeadLetterKey(KafkaTopics.ORDER_EVENTS + ".DLT", key)).isTrue();

        // The point of the fix: the consumer is still alive afterwards.
        EventEnvelope good = orderConfirmedEvent();
        publish(KafkaTopics.ORDER_EVENTS, good);

        assertThat(awaitNotification(good.eventId()).getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void anEventWhosePayloadDoesNotFit_isDeadLetteredWithoutRetries() throws Exception {

        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID().toString(),
                EventEnvelope.Types.ORDER_CONFIRMED, OffsetDateTime.now(), 1, null,
                objectMapper.valueToTree(Map.of("orderId", "not-a-number")));

        publish(KafkaTopics.ORDER_EVENTS, envelope);

        assertThat(awaitDeadLetter(KafkaTopics.ORDER_EVENTS + ".DLT", envelope.eventId())).hasSize(1);
        assertThat(notificationRepository.count()).isZero();
        then(mailSender).shouldHaveNoInteractions();
    }

    @Test
    void aDeliveryThatKeepsFailing_isMarkedFailedAndDeadLettered() throws Exception {

        willThrow(new NotificationDeliveryException("smtp is down"))
                .given(mailSender).send(anyString(), anyString(), anyString());

        EventEnvelope envelope = orderConfirmedEvent();

        publish(KafkaTopics.ORDER_EVENTS, envelope);

        List<ConsumerRecord<String, String>> deadLetters =
                awaitDeadLetter(KafkaTopics.ORDER_EVENTS + ".DLT", envelope.eventId());

        assertThat(deadLetters).hasSize(1);
        assertThat(deadLetters.get(0).value()).contains(envelope.eventId());

        Notification saved = awaitNotification(envelope.eventId());
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
        // The initial attempt plus two retries (retry-max-attempts=2 in this test).
        assertThat(saved.getRetryCount()).isEqualTo(3);
        assertThat(saved.getError()).contains("smtp is down");
        assertThat(saved.getSentAt()).isNull();

        // Never retired, so the admin retry endpoint is what picks it up next.
        assertThat(processedEventRepository.existsById(envelope.eventId())).isFalse();
    }

    // ------------------------------------------------------------- helpers

    private EventEnvelope orderConfirmedEvent() {

        OrderConfirmedPayload payload = new OrderConfirmedPayload(
                42L,
                "MS-000042",
                7L,
                CUSTOMER_EMAIL,
                List.of(new OrderConfirmedPayload.OrderLine(1L, "SKU-1", "Wireless Mouse", null,
                        new BigDecimal("1299.00"), 2, new BigDecimal("2598.00"))),
                new BigDecimal("2598.00"),
                new OrderConfirmedPayload.ShippingAddress("Asha Rao", "9876543210", "12 MG Road",
                        null, "Bengaluru", "KA", "560001", "India"));

        return new EventEnvelope(UUID.randomUUID().toString(), EventEnvelope.Types.ORDER_CONFIRMED,
                OffsetDateTime.now(), 1, null, objectMapper.valueToTree(payload));
    }

    private void publish(String topic, EventEnvelope envelope) throws Exception {
        kafkaTemplate.send(topic, String.valueOf(envelope.eventId()), envelope)
                .get(AWAIT.toSeconds(), TimeUnit.SECONDS);
    }

    /** Publishes raw bytes with a plain String serializer, bypassing the JSON serialization. */
    private void sendRaw(String topic, String key, String value) throws Exception {

        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        DefaultKafkaProducerFactory<String, String> factory =
                new DefaultKafkaProducerFactory<>(config);

        try {
            new KafkaTemplate<>(factory)
                    .send(topic, key, value)
                    .get(AWAIT.toSeconds(), TimeUnit.SECONDS);
        } finally {
            factory.destroy();
        }
    }

    private Notification awaitNotification(String eventId) throws InterruptedException {

        long deadline = System.nanoTime() + AWAIT.toNanos();

        while (System.nanoTime() < deadline) {
            Optional<Notification> found = notificationRepository.findByEventId(eventId);
            if (found.isPresent()) {
                return found.get();
            }
            Thread.sleep(100);
        }

        throw new AssertionError("No notification row was written for event " + eventId);
    }

    private void awaitProcessedEventCount(long expected) throws InterruptedException {

        long deadline = System.nanoTime() + AWAIT.toNanos();

        while (System.nanoTime() < deadline) {
            if (processedEventRepository.count() >= expected) {
                return;
            }
            Thread.sleep(100);
        }

        throw new AssertionError("Expected at least " + expected + " processed event(s)");
    }

    private List<ConsumerRecord<String, String>> awaitDeadLetter(String topic, String eventId)
            throws InterruptedException {

        return awaitDlt(topic, record -> record.value() != null && record.value().contains(eventId),
                "a dead letter for event " + eventId);
    }

    private boolean awaitDeadLetterKey(String topic, String key) throws InterruptedException {

        return !awaitDlt(topic, record -> key.equals(record.key()),
                "a dead letter with key " + key).isEmpty();
    }

    private List<ConsumerRecord<String, String>> awaitDlt(
            String topic,
            java.util.function.Predicate<ConsumerRecord<String, String>> match,
            String description) throws InterruptedException {

        long deadline = System.nanoTime() + AWAIT.toNanos();

        while (System.nanoTime() < deadline) {
            List<ConsumerRecord<String, String>> found = drainDlt(topic, match);
            if (!found.isEmpty()) {
                return found;
            }
            Thread.sleep(200);
        }

        throw new AssertionError("No " + description + " arrived on " + topic);
    }

    /**
     * Reads the dead-letter topic with a throwaway consumer group, so the assertion never races with
     * a listener. Records whose value could not be deserialized arrive with a null value, so the
     * predicate is free to match on the key or the headers instead.
     */
    private List<ConsumerRecord<String, String>> drainDlt(
            String topic,
            java.util.function.Predicate<ConsumerRecord<String, String>> match) {

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-assert-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        List<ConsumerRecord<String, String>> matches = new ArrayList<>();

        try (Consumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));

            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();

            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(300))) {
                    if (match.test(record)) {
                        matches.add(record);
                    }
                }
                if (!matches.isEmpty()) {
                    break;
                }
            }
        }

        return matches;
    }
}
