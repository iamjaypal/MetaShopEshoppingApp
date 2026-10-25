package com.shopMeta.notification_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Consumer de-duplication (spec 14.4). Kafka guarantees at-least-once delivery, so the same event can
 * arrive more than once; an event id present here has already been handled and is skipped.
 *
 * <p>The shape matches the table already created in inventory-service / order-service /
 * payment-service so the Kafka phase sees one consistent contract (D-0xx).
 */
@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 64, nullable = false)
    private String eventId;

    @Column(name = "event_type", length = 80)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
