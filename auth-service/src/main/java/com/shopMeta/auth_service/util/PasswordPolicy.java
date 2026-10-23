package com.shopMeta.auth_service.util;

/**
 * The password policy from spec 7.4: minimum 8 characters with at least one uppercase
 * letter, one lowercase letter, one digit and one special character.
 */
public final class PasswordPolicy {

    public static final String REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,100}$";

    public static final String MESSAGE =
            "Password must be 8-100 characters and contain at least one uppercase letter, "
                    + "one lowercase letter, one digit and one special character";

    private PasswordPolicy() {
    }
}
