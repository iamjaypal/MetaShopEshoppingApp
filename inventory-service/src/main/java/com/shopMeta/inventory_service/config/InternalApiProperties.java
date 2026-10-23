package com.shopMeta.inventory_service.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Shared secret for service-to-service calls (spec 5.4 / 18).
 *
 * <p>Every request to {@code /api/v1/internal/**} must carry {@code X-Internal-Token} with this
 * value. The gateway also refuses to route {@code /api/v1/internal/**} from the outside, so this is
 * defence in depth for calls that reach the service port directly.
 */
@Component
@ConfigurationProperties(prefix = "metashop.internal")
@Getter
@Setter
public class InternalApiProperties {

    /** Read from {@code metashop.internal.token} (env var {@code INTERNAL_TOKEN}). */
    private String token;

}
