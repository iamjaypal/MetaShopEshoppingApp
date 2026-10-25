package com.shopMeta.notification_service.service;

import com.shopMeta.notification_service.entity.NotificationType;

/**
 * One email, fully decided: who it goes to, what it says and which event produced it.
 *
 * <p>The output of {@link NotificationPlanner} and the input to {@link NotificationStore} and the mail
 * sender. It exists so the rendering step has no dependency on persistence and the delivery step has
 * no dependency on Thymeleaf.
 */
public record NotificationPlan(
        String eventId,
        Long userId,
        String email,
        NotificationType type,
        String subject,
        String body) {
}
