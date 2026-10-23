package com.shopMeta.inventory_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Public availability answer (spec 9.3). {@code availableQuantity} is capped by
 * {@code metashop.inventory.display-quantity-cap} so exact stock levels are not leaked.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilityResponse {

    private Long productId;

    private boolean inStock;

    /** Sellable units, capped for display ("only 3 left"). */
    private int availableQuantity;

}
