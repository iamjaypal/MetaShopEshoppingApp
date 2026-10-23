package com.shopMeta.order_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * MetaShop order service (spec 11).
 *
 * <p>Turns the caller's cart into an order: freezes product prices into line items, snapshots the
 * shipping address, reserves stock through inventory-service (idempotent per {@code orderId}) and
 * drives the lifecycle {@code CREATED -> CONFIRMED -> SHIPPED -> DELIVERED}, with {@code CANCELLED}
 * releasing whatever stock the order still holds.
 *
 * <p>{@link EnableFeignClients} wires the downstream clients (cart-service reads go out with the
 * caller's JWT, D-061; every internal call carries {@code X-Internal-Token}) and each is guarded by
 * a Resilience4j retry + circuit breaker (spec 5.8).
 */
@SpringBootApplication
@EnableFeignClients
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

}
