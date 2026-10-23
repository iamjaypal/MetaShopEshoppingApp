package com.shopMeta.payment_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * MetaShop payment service (spec 12).
 *
 * <p>Keeps the payment ledger: the customer pays for their order, the amount is read from
 * order-service's authoritative internal API (never from the client), the charge goes through the
 * mock gateway, and the outcome is reported back to order-service through its internal
 * {@code payment-confirmed} / {@code payment-failed} callbacks (D-071), which confirm or release
 * the reserved stock.
 *
 * <p>{@link EnableFeignClients} wires the order-service client; the call is guarded by a
 * Resilience4j retry + circuit breaker (spec 5.8) and carries {@code X-Internal-Token}.
 */
@SpringBootApplication
@EnableFeignClients
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

}
