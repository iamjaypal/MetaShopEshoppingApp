package com.shopMeta.order_service.dto;

import com.shopMeta.order_service.entity.OrderStatus;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Admin status update (spec 11): the status to move the order to. Only the progression the admin
 * actually controls is accepted — payment confirms orders, so {@code CONFIRMED} is not settable
 * from here.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusRequest {

    @NotNull(message = "status is required")
    private OrderStatus status;
}
