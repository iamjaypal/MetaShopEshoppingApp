package com.shopMeta.order_service.dto;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Checkout request (spec 11). The order is built from the caller's cart — the only decision the
 * caller makes is which saved address to ship to. There is deliberately no way to pass order
 * lines or a user id in the body (the cart and the token subject are authoritative).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotNull(message = "shippingAddressId is required")
    private Long shippingAddressId;
}
