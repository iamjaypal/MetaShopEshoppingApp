package com.shopMeta.order_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.shopMeta.order_service.entity.OrderStatus;
import com.shopMeta.order_service.entity.PaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The full order as returned by every order endpoint (spec 11): the frozen money snapshot, the
 * shipping-address snapshot, the lifecycle status and the frozen lines.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private Long id;

    private Long userId;

    private OrderStatus status;

    private PaymentStatus paymentStatus;

    private List<OrderItemResponse> items;

    private BigDecimal subtotal;

    private BigDecimal shippingFee;

    private BigDecimal total;

    private OrderAddressResponse shippingAddress;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
