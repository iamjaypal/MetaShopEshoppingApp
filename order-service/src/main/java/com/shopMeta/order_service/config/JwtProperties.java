package com.shopMeta.order_service.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT settings (spec 5.4). order-service does not issue tokens — it only verifies the ones
 * auth-service issued, so the secret and issuer must match auth-service exactly.
 *
 * <p>The caller's token is additionally <strong>forwarded</strong> to cart-service for the checkout
 * reads ({@code GET /api/v1/cart}, {@code POST /api/v1/cart/validate}, D-061): the cart belongs to
 * the buyer's session, not to a service.
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
