package com.shopMeta.inventory_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Bulk availability check for cart / order validation (spec 9.3,
 * {@code POST /internal/inventory/availability}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilityRequest {

    @NotEmpty(message = "items must not be empty")
    @Size(max = 100, message = "at most 100 items can be checked at once")
    @Valid
    private List<AvailabilityItemRequest> items;

}
