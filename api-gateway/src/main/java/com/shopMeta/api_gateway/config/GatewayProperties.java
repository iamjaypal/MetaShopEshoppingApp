package com.shopMeta.api_gateway.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Gateway configuration bound from {@code metashop.gateway.*} (spec 5.4 / 6.2 / 18).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "metashop.gateway")
public class GatewayProperties {

    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();
    private final RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class Jwt {

        /** Shared HS256 secret; must match auth-service ({@code JWT_SECRET}). */
        private String secret = "MetaShopDevOnlyJwtSecretKeyChangeMe0123456789abcdef";

        /** Expected {@code iss} claim. */
        private String issuer = "metashop-auth-service";
    }

    @Getter
    @Setter
    public static class Cors {

        /** Allowed browser origins (the Angular app). */
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:4200"));
    }

    @Getter
    @Setter
    public static class RateLimit {

        private boolean enabled = true;

        /** Requests allowed per {@link #windowSeconds} per client IP, per path. */
        private int requestsPerWindow = 5;

        private int windowSeconds = 60;

        /** Path prefixes that are rate limited (spec 18: login/register/forgot-password). */
        private List<String> paths = new ArrayList<>(List.of(
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/auth/forgot-password",
                "/api/v1/auth/reset-password"));
    }

}
