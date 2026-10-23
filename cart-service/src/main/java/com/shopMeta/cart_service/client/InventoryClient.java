package com.shopMeta.cart_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.shopMeta.cart_service.client.dto.AvailabilityBatchResponse;
import com.shopMeta.cart_service.client.dto.AvailabilityRequest;

/**
 * inventory-service's internal bulk availability check (spec 9.3).
 *
 * <p>The internal variant is used on purpose: it returns the exact sellable quantity, which is what
 * a cart needs to decide between "in stock", "only 3 left" and "out of stock" (the public endpoint
 * caps the number for display).
 */
@FeignClient(name = "inventory-service")
public interface InventoryClient {

    @PostMapping("/api/v1/internal/inventory/availability")
    AvailabilityBatchResponse checkAvailability(@RequestBody AvailabilityRequest request);
}
