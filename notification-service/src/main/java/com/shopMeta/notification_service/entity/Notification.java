package com.shopMeta.notification_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * One email this service tried to send (spec 13).
 *
 * <p>The row is written <em>before</em> the SMTP call so a delivery that dies half way is still
 * visible, and the rendered {@code body} is stored so an admin retry replays exactly what the user
 * would have received.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The event that produced this row. Unique, so a redelivered event updates the existing row
     * instead of sending the same email twice (see D-080).
     */
    @Column(name = "event_id", length = 64, unique = true)
    private String eventId;

    /** The recipient's user id, when the event carries one. NULL for admin alerts. */
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 255)
    private String subject;

    /** The rendered HTML body. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    /** Number of failed delivery attempts. */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /** Why the last attempt failed, truncated to the column width. */
    @Column(length = 255)
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** Marks a successful delivery. */
    public void markSent(LocalDateTime when) {
        this.status = NotificationStatus.SENT;
        this.sentAt = when;
        this.error = null;
    }

    /** Marks a failed delivery, counting the attempt and recording why. */
    public void markFailed(String reason) {
        this.status = NotificationStatus.FAILED;
        this.error = truncate(reason);
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }

    /** Re-queues the row for a manual retry from the admin API. */
    public void markPendingForRetry() {
        this.status = NotificationStatus.PENDING;
        this.error = null;
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 255 ? reason : reason.substring(0, 255);
    }
}
