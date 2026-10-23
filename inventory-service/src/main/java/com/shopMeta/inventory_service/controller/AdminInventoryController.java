package com.shopMeta.inventory_service.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopMeta.inventory_service.dto.InventoryPageResponse;
import com.shopMeta.inventory_service.dto.InventoryResponse;
import com.shopMeta.inventory_service.dto.StockCreateRequest;
import com.shopMeta.inventory_service.dto.StockUpdateRequest;
import com.shopMeta.inventory_service.security.AuthenticatedUser;
import com.shopMeta.inventory_service.service.InventoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Admin stock management (spec 9.3). ADMIN role only; every change is written to
 * {@code stock_movements} with the admin's email as the reference.
 */
@Tag(name = "Inventory (admin)", description = "Admin stock management")
@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryService inventoryService;

    /** Creates the stock row for a product. */
    @Operation(summary = "Create the stock row for a product")
    @PostMapping
    public ResponseEntity<InventoryResponse> createStock(
            @Valid @RequestBody StockCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {

        InventoryResponse created = inventoryService.createStock(request, actorName(actor));

        return ResponseEntity
                .created(URI.create("/api/v1/admin/inventory/" + created.getProductId()))
                .body(created);
    }

    /** Full stock view for one product, including reserved quantity and threshold. */
    @Operation(summary = "Get the full stock row for a product")
    @GetMapping("/{productId}")
    public ResponseEntity<InventoryResponse> getStock(@PathVariable Long productId) {

        return ResponseEntity.ok(inventoryService.getStock(productId));
    }

    /**
     * Sets or adjusts the available quantity. Exactly one of {@code quantity} (absolute) or
     * {@code adjustment} (signed delta) must be supplied; the movement is logged either way.
     */
    @Operation(summary = "Set or adjust the stock quantity of a product",
            description = "Supply either 'quantity' to set the absolute value or 'adjustment' for a "
                    + "signed delta. The change is recorded in stock_movements.")
    @PutMapping("/{productId}")
    public ResponseEntity<InventoryResponse> updateStock(
            @PathVariable Long productId,
            @Valid @RequestBody StockUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {

        return ResponseEntity.ok(
                inventoryService.updateStock(productId, request, actorName(actor)));
    }

    /**
     * Products at or below the low-stock threshold. Without {@code threshold} each product uses its
     * own {@code low_stock_threshold}.
     */
    @Operation(summary = "List products at or below their low-stock threshold")
    @GetMapping("/low-stock")
    public ResponseEntity<InventoryPageResponse> findLowStock(
            @RequestParam(required = false) Integer threshold,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(inventoryService.findLowStock(threshold, page, size));
    }

    private String actorName(AuthenticatedUser actor) {

        return actor != null && actor.email() != null ? actor.email() : "unknown-admin";
    }
}
