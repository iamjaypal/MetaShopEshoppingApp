package com.shopMeta.order_service.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirror of cart-service's {@code CartResponse} (spec 10.3), read through the buyer's own JWT
 * (D-061) — the cart belongs to the user's session, so there is deliberately no internal
 * "read someone's cart" endpoint.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CartView {

    private Long cartId;

    private Long userId;

    private List<CartLine> items;

    private int totalItems;

    private int distinctItems;

    private BigDecimal subtotal;

    private String currency;

    private boolean hasWarnings;

    private List<String> warnings;

    private LocalDateTime updatedAt;
}
