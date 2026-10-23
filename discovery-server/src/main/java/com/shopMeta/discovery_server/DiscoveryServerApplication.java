package com.shopMeta.discovery_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * MetaShop service registry (spec 6.1).
 *
 * <p>Eureka server on port 8761. Every other service registers here and is then reachable from
 * the API Gateway through {@code lb://<service-name>} URIs.
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }

}
