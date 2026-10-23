package com.shopMeta.payment_service.security;

import java.util.List;

/**
 * The caller identity carried by a verified access token (spec 7.4 claims {@code sub}, {@code email},
 * {@code roles}). The user id owns the payment: every customer-facing operation is scoped by it and
 * never by anything the request says.
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
