package com.shopMeta.auth_service.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * JWT settings (spec 7.4): secret, issuer and token lifetimes.
 * The secret is read from {@code metashop.jwt.secret} (env var {@code JWT_SECRET}).
 */
@Component
@ConfigurationProperties(prefix = "metashop.jwt")
@Getter
@Setter
public class JwtProperties {

    /** HMAC signing secret; must be at least 256 bit (32 bytes). */
    private String secret;

    private String issuer = "metashop-auth-service";

    /** Access-token lifetime, spec: 15 minutes. */
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    /** Refresh-token lifetime, spec: 7 days. */
    private Duration refreshTokenTtl = Duration.ofDays(7);
}
