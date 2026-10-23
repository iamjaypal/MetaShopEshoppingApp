package com.shopMeta.order_service.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One page of orders (spec 5.3 pagination convention).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPageResponse {

    private List<OrderResponse> content;

    private int page;

    private int size;

    private long totalElements;

    private int totalPages;

    private boolean last;
}
