package com.shopMeta.cart_service.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Shared secret for service-to-service calls (spec 5.4 / 18).
 *
 * <p>It is used in both directions: {@code /api/v1/internal/cart/**} requires it from callers, and
 * every outbound Feign call to product-service / inventory-service sends it. The gateway also
 * refuses to route {@code /api/v1/internal/**} from the outside, so this is defence in depth for
 * calls that reach a service port directly.
 */
@Component
@ConfigurationProperties(prefix = "metashop.internal")
@Getter
@Setter
public class InternalApiProperties {

    /** Read from {@code metashop.internal.token} (env var {@code INTERNAL_TOKEN}). */
    private String token;

}
