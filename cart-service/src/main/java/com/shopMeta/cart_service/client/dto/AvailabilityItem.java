package com.shopMeta.cart_service.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Mirror of inventory-service's {@code AvailabilityItemResponse} (spec 9.3). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityItem {

    private Long productId;

    private String sku;

    /** Exact sellable units; the internal endpoint does not cap the number. */
    private int available;

    /** Units the cart asked about. */
    private int requested;

    /** {@code available >= requested}. */
    private boolean sufficient;

    private boolean inStock;
}
