package com.shopMeta.auth_service.exception;

/**
 * The account has been disabled by an admin. Returns 403.
 */
public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException(String message) {
        super(message);
    }
}
