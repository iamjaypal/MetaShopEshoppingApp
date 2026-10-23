package com.shopMeta.payment_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.shopMeta.payment_service.client.dto.OrderView;

/**
 * order-service's internal API (spec 11 / D-071).
 *
 * <p>{@code name} is the Eureka application id, so Spring Cloud LoadBalancer picks a live instance.
 * The shared {@code X-Internal-Token} header is attached by
 * {@link com.shopMeta.payment_service.config.FeignInternalTokenConfig}.
 *
 * <p>Both callbacks are <strong>idempotent</strong>: a replayed success returns the confirmed
 * order, a replayed failure the cancelled one — which is what makes the retry policy on these
 * calls safe (D-071).
 */
@FeignClient(name = "order-service")
public interface OrderClient {

    /** The authoritative order state: owner, lifecycle status and frozen total. */
    @GetMapping("/api/v1/internal/orders/{id}")
    OrderView getOrder(@PathVariable("id") Long id);

    /** Reports a captured charge: the order moves CONFIRMED, stock is sold, cart cleared. */
    @PostMapping("/api/v1/internal/orders/{id}/payment-confirmed")
    void paymentConfirmed(@PathVariable("id") Long id);

    /** Reports a declined charge: the order is cancelled and the stock released. */
    @PostMapping("/api/v1/internal/orders/{id}/payment-failed")
    void paymentFailed(@PathVariable("id") Long id);
}
