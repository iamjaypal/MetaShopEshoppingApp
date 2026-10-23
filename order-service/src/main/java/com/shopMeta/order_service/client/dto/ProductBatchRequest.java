package com.shopMeta.order_service.client.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirror of product-service's {@code ProductBatchRequest} (spec 8.3): the ids to look up.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductBatchRequest {

    private List<Long> ids;
}
