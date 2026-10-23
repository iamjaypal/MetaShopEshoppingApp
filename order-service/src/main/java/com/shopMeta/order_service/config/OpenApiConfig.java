package com.shopMeta.order_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger metadata for order-service (spec 2 / 17).
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI orderServiceOpenApi() {

        SecurityScheme bearerScheme = new SecurityScheme()
                .name(SECURITY_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("MetaShop - Order Service API")
                        .description("Checkout and the order lifecycle. An order is created from "
                                + "the buyer's cart with frozen price and address snapshots "
                                + "(spec 10.2), holds stock through inventory-service and is "
                                + "confirmed by its payment through payment-service.")
                        .version("v1")
                        .contact(new Contact().name("MetaShop").email("admin@metashop.com"))
                        .license(new License().name("MIT")))
                .servers(List.of(new Server().url("/").description("Current host")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME, bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
