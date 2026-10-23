package com.shopMeta.order_service.config;

import feign.RequestInterceptor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.shopMeta.order_service.security.InternalTokenFilter;

/**
 * Attaches the shared {@code X-Internal-Token} to every outbound Feign call (spec 5.4).
 *
 * <p>Declared as a plain bean rather than per-client configuration so a new Feign client cannot
 * forget it. Receiving services (product-service today, auth-service partially) do not all check
 * the header yet (see D-022), but sending it is harmless and keeps order-service correct the
 * moment those sides are wired up.
 */
@Configuration
public class FeignInternalTokenConfig {

    @Bean
    public RequestInterceptor internalTokenRequestInterceptor(InternalApiProperties properties) {
        return template -> template.header(
                InternalTokenFilter.INTERNAL_TOKEN_HEADER, properties.getToken());
    }
}
