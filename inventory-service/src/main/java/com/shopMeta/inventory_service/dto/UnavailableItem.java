package com.shopMeta.inventory_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One line that could not be reserved, reported in the 409 body so order-service can tell the
 * customer exactly what is unavailable (spec 19: "Out of stock → 409 listing which items are
 * unavailable").
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnavailableItem {

    private Long productId;

    /** Units the order wanted. */
    private int requested;

    /** Units actually sellable. */
    private int available;

}
