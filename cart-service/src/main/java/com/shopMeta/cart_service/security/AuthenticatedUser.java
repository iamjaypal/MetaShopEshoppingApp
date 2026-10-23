package com.shopMeta.cart_service.security;

import java.util.List;

/**
 * The caller identity carried by a verified access token (spec 7.4 claims {@code sub}, {@code email},
 * {@code roles}). Used as the security-context principal so controllers can scope every cart
 * operation to the authenticated user — the user id is never taken from the request body (spec 10.3
 * / 18).
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
