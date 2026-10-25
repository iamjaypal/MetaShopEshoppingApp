package com.shopMeta.notification_service.event.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code UserRegisteredEvent} (spec 14.3) — triggers the welcome email.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserRegisteredPayload(
        Long userId,
        String email,
        String firstName) {
}
