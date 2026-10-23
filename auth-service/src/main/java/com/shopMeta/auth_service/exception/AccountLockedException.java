package com.shopMeta.auth_service.exception;

/**
 * The account is temporarily locked after too many failed logins (spec 7.4). Returns 423.
 */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException(String message) {
        super(message);
    }
}
