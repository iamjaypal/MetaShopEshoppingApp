package com.shopMeta.inventory_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Create / initialize the stock row for a product (spec 9.3, {@code POST /api/admin/inventory}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCreateRequest {

    @NotNull(message = "productId is required")
    private Long productId;

    @Size(max = 50, message = "sku must be at most 50 characters")
    private String sku;

    @NotNull(message = "quantity is required")
    @PositiveOrZero(message = "quantity must be zero or greater")
    private Integer quantity;

    /** Optional; falls back to {@code metashop.inventory.default-low-stock-threshold}. */
    @Min(value = 0, message = "lowStockThreshold must be zero or greater")
    private Integer lowStockThreshold;

}
