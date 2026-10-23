package com.shopMeta.order_service.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One frozen line of an order (spec 10.2): the product snapshot (sku, name, image, unit price)
 * taken at checkout. Live catalogue changes never move an existing order.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemResponse {

    private Long id;

    private Long productId;

    private String sku;

    private String name;

    private String imageUrl;

    private BigDecimal unitPrice;

    private int quantity;

    /** {@code unitPrice * quantity} at checkout prices. */
    private BigDecimal lineTotal;
}
