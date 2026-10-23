package com.shopMeta.inventory_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One reservation as reported back to the caller (order-service).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationItemResponse {

    private Long productId;

    private int quantity;

    /** {@code RESERVED}, {@code CONFIRMED}, {@code RELEASED} or {@code EXPIRED}. */
    private String status;

    private LocalDateTime expiresAt;

}
