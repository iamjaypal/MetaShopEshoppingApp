package com.shopMeta.notification_service.security;

import java.util.List;

/**
 * The caller identity carried by a verified access token (spec 7.4 claims {@code sub}, {@code email},
 * {@code roles}).
 */
public record AuthenticatedUser(Long userId, String email, List<String> roles) {

    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }

    @Override
    public String toString() {
        return email;
    }
}
