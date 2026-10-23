package com.shopMeta.order_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirror of product-service's {@code InternalProductResponse} (spec 8.3): the minimal snapshot
 * (id, sku, name, price, image, active) that checkout freezes into the order line.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductSnapshot {

    private Long id;

    private String sku;

    private String name;

    private java.math.BigDecimal price;

    private String imageUrl;

    private Boolean active;
}
