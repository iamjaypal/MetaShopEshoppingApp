package com.shopMeta.auth_service.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates cryptographically random, URL-safe opaque tokens (refresh tokens, password-reset
 * tokens). Only the SHA-256 hash of a generated value is ever persisted.
 */
public final class SecureTokens {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 64;

    private SecureTokens() {
    }

    public static String randomToken() {

        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
