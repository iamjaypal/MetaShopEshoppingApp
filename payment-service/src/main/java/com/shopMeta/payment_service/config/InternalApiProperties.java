package com.shopMeta.payment_service.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Shared secret for service-to-service calls (spec 5.4 / 18).
 *
 * <p>Every internal call payment-service makes (the order read and the payment result callbacks)
 * carries {@code X-Internal-Token}; the payment ledger's own internal endpoints (if added later)
 * would require the same token.
 */
@Component
@ConfigurationProperties(prefix = "metashop.internal")
@Getter
@Setter
public class InternalApiProperties {

    /** Read from {@code metashop.internal.token} (env var {@code INTERNAL_TOKEN}). */
    private String token;
}
