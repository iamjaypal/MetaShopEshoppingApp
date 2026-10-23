package com.ShopMeta.product_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Paginated envelope as described in spec section 5.2.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPageResponse {

    private List<ProductSummaryResponse> content;

    private int page;

    private int size;

    private long totalElements;

    private int totalPages;

    private boolean last;
}
