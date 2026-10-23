package com.shopMeta.inventory_service.dto;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for release / confirm (spec 9.3): the order whose reservations are affected.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderReservationRequest {

    @NotNull(message = "orderId is required")
    private Long orderId;

}
