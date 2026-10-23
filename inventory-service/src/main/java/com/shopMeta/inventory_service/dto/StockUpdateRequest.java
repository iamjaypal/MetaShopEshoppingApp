package com.shopMeta.inventory_service.dto;

import jakarta.validation.constraints.Min;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Set or adjust the stock of an existing product (spec 9.3,
 * {@code PUT /api/admin/inventory/{productId}}). Every change is written to
 * {@code stock_movements}.
 *
 * <p>Exactly one of {@link #quantity} (set the absolute value) or {@link #adjustment}
 * (a signed delta, e.g. {@code -2} for damaged units) must be supplied.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockUpdateRequest {

    /** New absolute available quantity. Mutually exclusive with {@link #adjustment}. */
    @Min(value = 0, message = "quantity must be zero or greater")
    private Integer quantity;

    /** Signed delta applied to the available quantity. Mutually exclusive with {@link #quantity}. */
    private Integer adjustment;

    /** Optional new threshold. */
    @Min(value = 0, message = "lowStockThreshold must be zero or greater")
    private Integer lowStockThreshold;

}
