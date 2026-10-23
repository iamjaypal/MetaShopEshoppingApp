package com.shopMeta.inventory_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Atomic all-or-nothing reservation request (spec 9.3, {@code POST /internal/inventory/reserve}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReserveRequest {

    @NotNull(message = "orderId is required")
    private Long orderId;

    @NotEmpty(message = "items must not be empty")
    @Size(max = 100, message = "at most 100 items can be reserved at once")
    @Valid
    private List<ReservationItemRequest> items;

}
