package com.ShopMeta.product_service.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProductBatchRequest {

    @NotEmpty(message = "ids must not be empty")
    @Size(max = 100, message = "At most 100 ids are allowed per batch")
    private List<Long> ids;
}
