package com.shopMeta.inventory_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopMeta.inventory_service.dto.AvailabilityResponse;
import com.shopMeta.inventory_service.service.InventoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Public stock availability (spec 9.3). No authentication: the storefront needs this to render
 * "in stock" / "only 3 left" on a product page.
 */
@Tag(name = "Inventory (public)", description = "Public stock availability lookup")
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * {@code {productId, inStock, availableQuantity}}. The quantity is capped for display, so the
     * endpoint cannot be scraped for exact stock levels.
     */
    @Operation(summary = "Get stock availability for a product",
            description = "Public. availableQuantity is capped by "
                    + "metashop.inventory.display-quantity-cap so exact stock is not exposed.")
    @GetMapping("/{productId}")
    public ResponseEntity<AvailabilityResponse> getAvailability(@PathVariable Long productId) {

        return ResponseEntity.ok(inventoryService.getAvailability(productId));
    }
}
