package com.shopMeta.cart_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * MetaShop cart service (spec 10).
 *
 * <p>Owns the user's cart but deliberately <em>not</em> the price: every read enriches the stored
 * {@code productId} + {@code quantity} lines with live data from product-service and
 * inventory-service, so a cart can never show a stale price (spec 10.2).
 *
 * <p>{@link EnableFeignClients} wires the two declarative clients used for those lookups.
 */
@SpringBootApplication
@EnableFeignClients
public class CartServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }

}
