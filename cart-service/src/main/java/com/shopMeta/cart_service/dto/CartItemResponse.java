package com.shopMeta.cart_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One rendered cart line: the stored {@code productId} + {@code quantity} enriched with the live
 * catalogue price and the live stock level (spec 10.2).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemResponse {

    @Schema(description = "Id to use with PUT/DELETE /api/v1/cart/items/{itemId}")
    private Long itemId;

    private Long productId;

    private String sku;

    private String name;

    private String imageUrl;

    /** Live price from product-service — never a stored copy. */
    private BigDecimal unitPrice;

    /** Catalogue price when the line was added, kept only to detect a change. */
    private BigDecimal addedPrice;

    /** True when the live price differs from {@code addedPrice}. */
    private boolean priceChanged;

    private int quantity;

    /** {@code unitPrice * quantity}. */
    private BigDecimal lineTotal;

    /** Exact sellable units from inventory-service. */
    private int availableQuantity;

    private boolean inStock;

    /** {@code availableQuantity >= quantity} — the line can be ordered right now. */
    private boolean sufficient;

    /** False when the product was deactivated or deleted in the catalogue. */
    private boolean productActive;

    /** Human-readable problem with this line, or {@code null} when there is none. */
    private String warning;
}
