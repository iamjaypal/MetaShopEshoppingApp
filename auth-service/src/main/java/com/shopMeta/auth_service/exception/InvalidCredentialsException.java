package com.shopMeta.auth_service.exception;

/**
 * Bad email/password. Returns 401 and never reveals whether the email exists.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
