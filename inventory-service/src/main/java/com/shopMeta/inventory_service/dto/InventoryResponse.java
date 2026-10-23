package com.shopMeta.inventory_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Full stock view for admins (spec 9.3). Unlike {@link AvailabilityResponse} this exposes the
 * exact counters.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryResponse {

    private Long productId;

    private String sku;

    private int availableQuantity;

    private int reservedQuantity;

    private int lowStockThreshold;

    private boolean lowStock;

    private boolean inStock;

    private LocalDateTime updatedAt;

}
