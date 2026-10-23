package com.shopMeta.cart_service.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Mirror of inventory-service's {@code AvailabilityBatchResponse} (spec 9.3). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityBatchResponse {

    private List<AvailabilityItem> items;

    private boolean allAvailable;
}
