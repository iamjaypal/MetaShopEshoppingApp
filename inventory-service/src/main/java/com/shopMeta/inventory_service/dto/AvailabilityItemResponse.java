package com.shopMeta.inventory_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-product answer of the bulk availability check.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilityItemResponse {

    private Long productId;

    private String sku;

    /** Exact sellable units — internal callers need the real number. */
    private int available;

    /** Units the caller asked about. */
    private int requested;

    /** {@code available >= requested}. */
    private boolean sufficient;

    private boolean inStock;

}
