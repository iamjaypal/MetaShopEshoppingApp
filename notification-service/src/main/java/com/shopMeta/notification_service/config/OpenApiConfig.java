package com.shopMeta.notification_service.config;

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
 * OpenAPI / Swagger metadata for notification-service (spec 2 / 17).
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI notificationServiceOpenApi() {

        SecurityScheme bearerScheme = new SecurityScheme()
                .name(SECURITY_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("MetaShop - Notification Service API")
                        .description("An event consumer that turns domain events into HTML emails. "
                                + "It consumes user.events, order.events, payment.events and "
                                + "inventory.events, renders a Thymeleaf template per event type, "
                                + "sends it over SMTP and records the outcome. Failed deliveries "
                                + "are retried three times and then dead-lettered. The only HTTP "
                                + "surface is the admin notification log (spec 13).")
                        .version("v1")
                        .contact(new Contact().name("MetaShop").email("admin@metashop.com"))
                        .license(new License().name("MIT")))
                .servers(List.of(new Server().url("/").description("Current host")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME, bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
