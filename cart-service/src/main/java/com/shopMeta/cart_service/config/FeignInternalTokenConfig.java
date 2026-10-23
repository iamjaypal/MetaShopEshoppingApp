package com.shopMeta.cart_service.config;

import feign.RequestInterceptor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.shopMeta.cart_service.security.InternalTokenFilter;

/**
 * Attaches the shared {@code X-Internal-Token} to every outbound Feign call (spec 5.4).
 *
 * <p>Declared as a plain bean rather than per-client configuration so a new Feign client cannot
 * forget it. product-service does not check the header yet (see D-022) but sending it is harmless
 * and keeps cart-service correct the moment that side is wired up.
 */
@Configuration
public class FeignInternalTokenConfig {

    @Bean
    public RequestInterceptor internalTokenRequestInterceptor(InternalApiProperties properties) {
        return template -> template.header(
                InternalTokenFilter.INTERNAL_TOKEN_HEADER, properties.getToken());
    }
}
