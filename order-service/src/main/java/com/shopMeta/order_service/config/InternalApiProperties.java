package com.shopMeta.order_service.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Shared secret for service-to-service calls (spec 5.4 / 18).
 *
 * <p>Every internal call order-service makes (inventory reserve/confirm/release, product batch,
 * auth address snapshot, cart clear) and every internal call it receives
 * ({@code /api/v1/internal/orders/**}) carries {@code X-Internal-Token} with this value.
 */
@Component
@ConfigurationProperties(prefix = "metashop.internal")
@Getter
@Setter
public class InternalApiProperties {

    /** Read from {@code metashop.internal.token} (env var {@code INTERNAL_TOKEN}). */
    private String token;

}
