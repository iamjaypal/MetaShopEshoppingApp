package com.ShopMeta.product_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Internal product snapshot used by cart-service and order-service (spec section 8.3
 * POST /internal/products/batch). Deliberately minimal: id, sku, name, price, image, active.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalProductResponse {

    private Long id;

    private String sku;

    private String name;

    private BigDecimal price;

    private String imageUrl;

    private Boolean active;
}
