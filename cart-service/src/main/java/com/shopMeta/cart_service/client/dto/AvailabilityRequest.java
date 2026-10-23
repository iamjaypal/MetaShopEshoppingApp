package com.shopMeta.cart_service.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Request body of {@code POST /api/v1/internal/inventory/availability} (spec 9.3). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityRequest {

    private List<AvailabilityLine> items;
}
