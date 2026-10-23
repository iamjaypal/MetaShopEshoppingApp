package com.shopMeta.payment_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.shopMeta.payment_service.entity.PaymentMethod;
import com.shopMeta.payment_service.entity.PaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One payment attempt as returned by every payment endpoint (spec 12).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private Long id;

    private Long orderId;

    private Long userId;

    private BigDecimal amount;

    private PaymentMethod method;

    private PaymentStatus status;

    /** Reference returned by the (mock) gateway, set once the charge was processed. */
    private String gatewayReference;

    /** Why the charge was declined or could not be applied, set on FAILED. */
    private String failureReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
