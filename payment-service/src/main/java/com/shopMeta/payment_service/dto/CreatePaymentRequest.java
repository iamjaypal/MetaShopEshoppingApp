package com.shopMeta.payment_service.dto;

import com.shopMeta.payment_service.entity.PaymentMethod;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * "Take a payment for this order." Deliberately minimal: the amount is not in the request — the
 * order's authoritative total is read from order-service (spec 12). The method is how the customer
 * chose to pay; the charge itself goes through the mock gateway.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {

    @NotNull(message = "orderId is required")
    private Long orderId;

    @NotNull(message = "method is required (CARD, UPI or WALLET)")
    private PaymentMethod method;
}
