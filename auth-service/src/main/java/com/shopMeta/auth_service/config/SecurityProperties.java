package com.shopMeta.auth_service.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Account-lockout settings (spec 7.4): lock the account for a while after N failed logins.
 */
@Component
@ConfigurationProperties(prefix = "metashop.security")
@Getter
@Setter
public class SecurityProperties {

    private int maxFailedLoginAttempts = 5;

    private Duration lockDuration = Duration.ofMinutes(15);

    /** Lifetime of a password-reset token. */
    private Duration passwordResetTtl = Duration.ofMinutes(30);
}
