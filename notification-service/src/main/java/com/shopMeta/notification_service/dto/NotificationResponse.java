package com.shopMeta.notification_service.dto;

import com.shopMeta.notification_service.entity.NotificationStatus;
import com.shopMeta.notification_service.entity.NotificationType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One row of the admin notification log (spec 13). The rendered {@code body} is deliberately absent
 * from the list view — it is fetched per notification from the detail endpoint instead, so a page of
 * results stays small.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private Long id;

    /** The event that produced this notification; null for notifications with no source event. */
    private String eventId;

    private Long userId;

    private String email;

    private NotificationType type;

    private String subject;

    private NotificationStatus status;

    @Schema(description = "Number of failed delivery attempts")
    private int retryCount;

    /** Why the last attempt failed; null once the notification is sent. */
    private String error;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;
}
