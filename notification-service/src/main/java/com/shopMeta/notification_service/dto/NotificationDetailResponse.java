package com.shopMeta.notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One notification including the rendered HTML that was actually sent. Used by the detail endpoint,
 * which is where an admin goes to answer "what did the customer receive?".
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDetailResponse {

    private NotificationResponse notification;

    /** The rendered HTML body, exactly as it was (or will be) sent. */
    private String body;
}
