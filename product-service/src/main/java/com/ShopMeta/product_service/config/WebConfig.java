package com.ShopMeta.product_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves locally-stored uploaded files (product images) under the configured public base URL.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${metashop.storage.location:uploads}")
    private String storageLocation;

    @Value("${metashop.storage.public-base-url:/uploads}")
    private String publicBaseUrl;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        Path directory = Paths.get(storageLocation).toAbsolutePath().normalize();

        registry.addResourceHandler(publicBaseUrl + "/**")
                .addResourceLocations(directory.toUri().toString());
    }
}
