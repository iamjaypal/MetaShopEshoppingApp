package com.ShopMeta.product_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BrandRequest {

    @NotBlank(message = "Brand name is required")
    @Size(max = 100, message = "Brand name cannot exceed 100 characters")
    private String name;

    @Size(max = 120, message = "Slug cannot exceed 120 characters")
    private String slug;

    @Size(max = 500, message = "Logo URL cannot exceed 500 characters")
    private String logoUrl;

    private Boolean active;
}
