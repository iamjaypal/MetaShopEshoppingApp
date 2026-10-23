package com.shopMeta.cart_service.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Request body of {@code POST /api/v1/internal/products/batch}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductBatchRequest {

    private List<Long> ids;
}
