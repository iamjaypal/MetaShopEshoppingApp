package com.shopMeta.auth_service.exception;

/**
 * A refresh token or password-reset token is missing, expired, already used or revoked.
 * Returns 401.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
