package com.shopMeta.api_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * MetaShop API Gateway (spec 6.2).
 *
 * <p>Single public entry point on port 8080. Routes to services through Eureka
 * ({@code lb://<service-name>}), validates the JWT, propagates the {@code X-User-*} headers and
 * the correlation id, applies CORS and rate limiting, and returns a single JSON error shape.
 *
 * <p>Built on Spring Cloud Gateway's WebFlux server: this module must not depend on
 * {@code spring-boot-starter-webmvc}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

}
