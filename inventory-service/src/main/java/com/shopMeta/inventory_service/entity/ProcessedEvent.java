package com.shopMeta.inventory_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * De-duplication table for Kafka consumers (spec 9.2 / 14.4).
 *
 * <p>A consumer records the {@code eventId} in the same transaction as the work it performed, so a
 * redelivered message is skipped. Created now so the schema is complete; the consumer that writes
 * to it arrives with the Kafka phase.
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
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "event_type", length = 80)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

}
