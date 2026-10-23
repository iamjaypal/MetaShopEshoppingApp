package com.shopMeta.order_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The only cart fields checkout needs, mirrored from cart-service's {@code CartItemResponse}
 * (spec 10.3): which product and how many. Everything else (prices, warnings) is re-derived by
 * order-service from the product snapshot it fetches itself.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CartLine {

    private Long itemId;

    private Long productId;

    private int quantity;
}
