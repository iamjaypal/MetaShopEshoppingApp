package com.shopMeta.notification_service.kafka;

import com.shopMeta.notification_service.event.EventEnvelope;
import com.shopMeta.notification_service.event.KafkaTopics;
import com.shopMeta.notification_service.exception.MalformedEventException;
import com.shopMeta.notification_service.service.NotificationService;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The only entry point for domain events (spec 13 / 14.1).
 *
 * <p>One listener on four topics rather than four listeners: they share a consumer group, so a single
 * listener keeps the partition assignment and the offsets in one place and there is only one place
 * where "an event became an email" happens.
 *
 * <p>The offset is committed manually and only after {@link NotificationService#handle} returns
 * (spec 14.4). A delivery failure propagates to the {@code DefaultErrorHandler}, which retries three
 * times with exponential backoff and then publishes the record to {@code <topic>.DLT}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = {
            KafkaTopics.USER_EVENTS,
            KafkaTopics.ORDER_EVENTS,
            KafkaTopics.PAYMENT_EVENTS,
            KafkaTopics.INVENTORY_EVENTS
    })
    public void onEvent(ConsumerRecord<String, EventEnvelope> record, Acknowledgment acknowledgment) {

        EventEnvelope envelope = record.value();

        if (envelope == null) {
            // ErrorHandlingDeserializer turns an unparseable record into a null value plus an
            // exception header, so the container keeps polling instead of throwing on every fetch.
            // Such a record can never be handled, so it is dead-lettered rather than skipped: the
            // original bytes are preserved on <topic>.DLT for an operator to look at.
            throw new MalformedEventException("The record at " + record.topic() + "-"
                    + record.partition() + "@" + record.offset()
                    + " could not be deserialized into an EventEnvelope", null);
        }

        if (log.isDebugEnabled()) {
            log.debug("Received {} from {} partition {} offset {}",
                    envelope.eventType(), record.topic(), record.partition(), record.offset());
        }

        notificationService.handle(envelope);

        // Reached only when the notification was persisted as SENT, or when the event was
        // deliberately skipped. A delivery failure has already been thrown.
        acknowledgment.acknowledge();
    }
}
