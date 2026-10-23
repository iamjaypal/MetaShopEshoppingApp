package com.shopMeta.order_service.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirror of inventory-service's {@code OrderReservationRequest} (spec 9.3): the order whose
 * reservations confirm or release.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderReservationRequest {

    private Long orderId;
}
