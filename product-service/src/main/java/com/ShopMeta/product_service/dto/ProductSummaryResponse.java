package com.ShopMeta.product_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lightweight product representation used by catalog listing (spec section 8.3 GET /api/products).
 * Omits the full image/attribute collections to keep list payloads small.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSummaryResponse {

    private Long id;

    private String sku;

    private String name;

    private String slug;

    private String shortDescription;

    private BigDecimal price;

    private BigDecimal mrp;

    private String currency;

    private Long categoryId;
    private String categoryName;

    private Long brandId;
    private String brandName;

    /** Primary image URL (falls back to the first image), or null when the product has no images. */
    private String imageUrl;

    private Boolean active;

    private BigDecimal averageRating;

    private Integer ratingCount;

    private LocalDateTime createdAt;
}
