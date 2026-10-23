package com.ShopMeta.product_service.dto;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductStatusRequest {

    @NotNull(message = "active is required")
    private Boolean active;
}
