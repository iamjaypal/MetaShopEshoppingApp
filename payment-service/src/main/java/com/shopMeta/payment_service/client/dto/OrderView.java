package com.shopMeta.payment_service.client.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The order fields payment-service needs, mirrored from order-service's internal read
 * ({@code GET /api/v1/internal/orders/{id}}, plain JSON, D-022): who owns it, whether it is still
 * waiting for payment, and the authoritative total to charge.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderView {

    private Long id;

    private Long userId;

    /** {@code CREATED}, {@code CONFIRMED}, {@code SHIPPED}, {@code DELIVERED} or {@code CANCELLED}. */
    private String status;

    private BigDecimal total;
}
