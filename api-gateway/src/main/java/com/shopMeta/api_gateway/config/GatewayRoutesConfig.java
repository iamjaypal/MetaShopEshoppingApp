package com.shopMeta.api_gateway.config;

import static com.shopMeta.api_gateway.security.AuthRequirement.ADMIN;
import static com.shopMeta.api_gateway.security.AuthRequirement.JWT;
import static com.shopMeta.api_gateway.security.AuthRequirement.METADATA_KEY;
import static com.shopMeta.api_gateway.security.AuthRequirement.PUBLIC;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway route table (spec 6.2).
 *
 * <p>Paths are versioned as {@code /api/v1/**} (see docs/DECISIONS.md D-003) and are forwarded to
 * the downstream services unchanged. Each route carries an {@link com.shopMeta.api_gateway.security.AuthRequirement}
 * so the JWT filter does not have to hard-code path rules.
 *
 * <p>Only services that are actually deployed get a route: auth-service, product-service,
 * inventory-service, cart-service and order-service today. Routes for payment-service are added
 * as those services land. {@code /api/v1/internal/**} is deliberately <em>not</em> routed: internal,
 * service-to-service endpoints must never be reachable from the outside (spec 5.4 / 19).
 */
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator metaShopRoutes(RouteLocatorBuilder builder) {
        return builder.routes()

                // ---------- auth-service (:8081) ----------
                .route("auth-service-public", r -> r
                        .path("/api/v1/auth/**")
                        .metadata(METADATA_KEY, PUBLIC.name())
                        .uri("lb://auth-service"))

                .route("auth-service-profile", r -> r
                        .path("/api/v1/users/**")
                        .metadata(METADATA_KEY, JWT.name())
                        .uri("lb://auth-service"))

                .route("auth-service-admin", r -> r
                        .path("/api/v1/admin/users/**")
                        .metadata(METADATA_KEY, ADMIN.name())
                        .uri("lb://auth-service"))

                // ---------- product-service (:8082) ----------
                .route("product-service-admin", r -> r
                        .path("/api/v1/admin/products/**",
                                "/api/v1/admin/categories/**",
                                "/api/v1/admin/brands/**")
                        .metadata(METADATA_KEY, ADMIN.name())
                        .uri("lb://product-service"))

                .route("product-service-public", r -> r
                        .path("/api/v1/products/**",
                                "/api/v1/categories/**",
                                "/api/v1/brands/**")
                        .metadata(METADATA_KEY, PUBLIC.name())
                        .uri("lb://product-service"))

                // ---------- inventory-service (:8083) ----------
                .route("inventory-service-admin", r -> r
                        .path("/api/v1/admin/inventory/**")
                        .metadata(METADATA_KEY, ADMIN.name())
                        .uri("lb://inventory-service"))

                .route("inventory-service-public", r -> r
                        .path("/api/v1/inventory/**")
                        .metadata(METADATA_KEY, PUBLIC.name())
                        .uri("lb://inventory-service"))

                // ---------- cart-service (:8084) ----------
                // The whole cart is user-scoped, so every endpoint needs a JWT. There is no public
                // cart read (spec 10.3). /api/v1/internal/cart/** stays unrouted.
                .route("cart-service", r -> r
                        .path("/api/v1/cart/**")
                        .metadata(METADATA_KEY, JWT.name())
                        .uri("lb://cart-service"))

                // ---------- order-service (:8085) ----------
                // Checkout and the buyer's order history are user-scoped (JWT); fulfilment is
                // admin-only. /api/v1/internal/orders/** (the payment-result callbacks) stays
                // unrouted — payment-service calls order-service directly.
                .route("order-service-admin", r -> r
                        .path("/api/v1/admin/orders/**")
                        .metadata(METADATA_KEY, ADMIN.name())
                        .uri("lb://order-service"))

                .route("order-service", r -> r
                        .path("/api/v1/orders/**")
                        .metadata(METADATA_KEY, JWT.name())
                        .uri("lb://order-service"))

                .build();
    }

}
