package com.shopMeta.notification_service.event.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code PasswordResetRequestedEvent} (spec 14.3) — triggers the password reset email.
 *
 * <p>The token is placed in the link. It is a single-use, short-lived, hashed-at-rest token on the
 * auth side (D-033), and it is never logged here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PasswordResetRequestedPayload(
        Long userId,
        String email,
        String resetToken) {
}
