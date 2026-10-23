package com.shopMeta.inventory_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Result of reserve / release / confirm (spec 9.3).
 *
 * <p>{@code status} is the status the reservation(s) of this order now have; {@code items} carries
 * the per-product detail.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationResponse {

    private Long orderId;

    private String status;

    private List<ReservationItemResponse> items;

}
