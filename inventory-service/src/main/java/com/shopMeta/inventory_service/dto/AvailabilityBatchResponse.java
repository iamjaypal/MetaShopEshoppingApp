package com.shopMeta.inventory_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Response of the bulk availability check (spec 9.3).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilityBatchResponse {

    private List<AvailabilityItemResponse> items;

    /** True when every requested line can be satisfied. */
    private boolean allAvailable;

}
