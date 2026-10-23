package com.shopMeta.inventory_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopMeta.inventory_service.dto.AvailabilityBatchResponse;
import com.shopMeta.inventory_service.dto.AvailabilityRequest;
import com.shopMeta.inventory_service.dto.OrderReservationRequest;
import com.shopMeta.inventory_service.dto.ReservationResponse;
import com.shopMeta.inventory_service.dto.ReserveRequest;
import com.shopMeta.inventory_service.service.ReservationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Service-to-service reservation API (spec 9.3) used by order-service and cart-service.
 *
 * <p>Not reachable through the API Gateway (see D-041); callers hit the service port directly and
 * must send {@code X-Internal-Token}. Responses are plain JSON — no {@code ApiResponse} envelope —
 * so the Feign clients stay trivial (D-022).
 */
@Tag(name = "Inventory (internal)", description = "Service-to-service reservation API")
@RestController
@RequestMapping("/api/v1/internal/inventory")
@RequiredArgsConstructor
public class InternalInventoryController {

    private final ReservationService reservationService;

    /**
     * Holds stock for an order, all-or-nothing. Returns 409 with the offending lines when any item
     * is unavailable. Idempotent per {@code orderId}.
     */
    @Operation(summary = "Reserve stock for an order (all-or-nothing)",
            description = "Requires X-Internal-Token. Returns 409 listing the unavailable items when "
                    + "any line cannot be satisfied; nothing is held in that case. A replay for the "
                    + "same orderId returns the existing reservations.")
    @PostMapping("/reserve")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReserveRequest request) {

        return ResponseEntity.ok(reservationService.reserve(request));
    }

    /** Returns every held line of an order to available stock. Idempotent. */
    @Operation(summary = "Release the stock held for an order",
            description = "Requires X-Internal-Token. Idempotent: lines already released or expired "
                    + "are left alone.")
    @PostMapping("/release")
    public ResponseEntity<ReservationResponse> release(
            @Valid @RequestBody OrderReservationRequest request) {

        return ResponseEntity.ok(reservationService.release(request.getOrderId()));
    }

    /** Turns the holds of an order into sales (payment succeeded). Idempotent. */
    @Operation(summary = "Confirm the stock held for an order",
            description = "Requires X-Internal-Token. Idempotent: already-confirmed lines are left "
                    + "alone. A line that was released or expired returns 422.")
    @PostMapping("/confirm")
    public ResponseEntity<ReservationResponse> confirm(
            @Valid @RequestBody OrderReservationRequest request) {

        return ResponseEntity.ok(reservationService.confirm(request.getOrderId()));
    }

    /** Bulk availability check for cart / order validation, with exact quantities. */
    @Operation(summary = "Bulk availability check",
            description = "Requires X-Internal-Token. Internal callers see the exact quantity; "
                    + "'quantity' is optional per line and defaults to 1.")
    @PostMapping("/availability")
    public ResponseEntity<AvailabilityBatchResponse> availability(
            @Valid @RequestBody AvailabilityRequest request) {

        return ResponseEntity.ok(reservationService.checkAvailability(request));
    }
}
