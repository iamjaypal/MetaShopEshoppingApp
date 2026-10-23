package com.shopMeta.order_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopMeta.order_service.dto.OrderResponse;
import com.shopMeta.order_service.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Service-to-service payment results (spec 11), for payment-service (built next).
 *
 * <p>Not reachable through the API Gateway (see D-041); the caller hits the service port directly
 * and must send {@code X-Internal-Token}. Responses are plain JSON — no {@code ApiResponse}
 * envelope — so the Feign client stays trivial (D-022).
 *
 * <p>Both endpoints are idempotent: a replayed success returns the confirmed order, a replayed
 * failure the cancelled one.
 */
@Tag(name = "Orders (internal)", description = "Service-to-service payment results")
@RestController
@RequestMapping("/api/v1/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;

    /**
     * The authoritative order state for internal callers — payment-service reads owner, status and
     * the frozen total from here before taking a charge (it never trusts the client's amount).
     * Plain JSON (no envelope), same as the other internal APIs (D-022).
     */
    @Operation(summary = "Order snapshot for internal callers",
            description = "Requires X-Internal-Token. Returns the order exactly as the public "
                    + "detail endpoint does, including the owner id — the caller (payment-service) "
                    + "checks ownership and the lifecycle status itself.")
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable("id") Long id) {

        return ResponseEntity.ok(orderService.getInternalOrder(id));
    }

    @Operation(summary = "Report a successful payment",
            description = "Requires X-Internal-Token. Moves the order CREATED → CONFIRMED, turns "
                    + "the stock holds into sales and empties the buyer's cart. 422 when the "
                    + "order is not waiting for payment any more.")
    @PostMapping("/{id}/payment-confirmed")
    public ResponseEntity<OrderResponse> paymentConfirmed(@PathVariable("id") Long id) {

        return ResponseEntity.ok(orderService.paymentConfirmed(id));
    }

    @Operation(summary = "Report a failed payment",
            description = "Requires X-Internal-Token. Cancels the order and releases the stock "
                    + "holds. 422 when the order is not waiting for payment any more.")
    @PostMapping("/{id}/payment-failed")
    public ResponseEntity<OrderResponse> paymentFailed(@PathVariable("id") Long id) {

        return ResponseEntity.ok(orderService.paymentFailed(id));
    }
}
