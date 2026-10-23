package com.shopMeta.order_service.config;

import feign.RequestInterceptor;

import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Per-client Feign configuration for {@link com.shopMeta.order_service.client.CartSessionClient}
 * only (D-061): forwards the caller's {@code Authorization: Bearer <jwt>} header to cart-service,
 * so the cart read and the pre-checkout validation run as <em>the buyer</em>.
 *
 * <p>Deliberately <strong>not</strong> annotated with {@code @Configuration}: as a per-client
 * configuration class it is instantiated in the Feign child context. Annotated, its beans would
 * apply to every Feign client in the application, forwarding user tokens to product- and
 * inventory-service where they are not needed.
 *
 * <p>The checkout request thread is blocked while Feign runs (MVC, not WebFlux), so the current
 * request's headers are available through {@link RequestContextHolder}.
 */
public class FeignUserTokenConfig {

    public static final String AUTHORIZATION_HEADER = "Authorization";

    @Bean
    public RequestInterceptor userAuthorizationForwardInterceptor() {

        return template -> {
            if (RequestContextHolder.getRequestAttributes()
                    instanceof ServletRequestAttributes attributes) {

                String authorization = attributes.getRequest().getHeader(AUTHORIZATION_HEADER);
                if (authorization != null && !authorization.isBlank()) {
                    template.header(AUTHORIZATION_HEADER, authorization);
                }
            }
        };
    }
}
