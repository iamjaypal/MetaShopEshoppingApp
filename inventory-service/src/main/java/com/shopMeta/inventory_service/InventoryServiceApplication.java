package com.shopMeta.inventory_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MetaShop inventory service (spec 9).
 *
 * <p>Owns {@code availableQuantity} / {@code reservedQuantity} and is the only component allowed to
 * decide whether stock can be taken, so it is the single place that prevents overselling.
 *
 * <p>{@link EnableScheduling} is needed for the reservation-expiry sweeper (spec 9.4).
 */
@SpringBootApplication
@EnableScheduling
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

}
