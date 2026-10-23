package com.shopMeta.inventory_service.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT settings (spec 5.4). inventory-service does not issue tokens — it only verifies the ones
 * auth-service issued, so the secret and issuer must match auth-service exactly.
 */
@Component
@ConfigurationProperties(prefix = "metashop.jwt")
@Getter
@Setter
public class JwtProperties {

    /** HMAC signing secret; must be at least 256 bit (32 bytes). */
    private String secret;

    private String issuer = "metashop-auth-service";
}
