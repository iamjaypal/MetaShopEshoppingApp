package com.shopMeta.cart_service.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Mirror of product-service's {@code InternalProductResponse} (spec 8.3). Services never share
 * entity classes, so each side owns its own view of the contract (spec 3 / D-022).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSnapshot {

    private Long id;

    private String sku;

    private String name;

    private BigDecimal price;

    private String imageUrl;

    private Boolean active;
}
