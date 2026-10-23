package com.ShopMeta.product_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;

    private String sku;

    private String name;

    private String slug;

    private String shortDescription;

    private String description;

    private BigDecimal price;

    private BigDecimal mrp;

    private String currency;

    private Long categoryId;
    private String categoryName;

    private Long brandId;
    private String brandName;

    private Boolean active;

    private BigDecimal averageRating;

    private Integer ratingCount;

    private List<ProductImageResponse> images;

    private List<ProductAttributeResponse> attributes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
