package com.shopMeta.order_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopMeta.order_service.dto.OrderPageResponse;
import com.shopMeta.order_service.dto.OrderResponse;
import com.shopMeta.order_service.dto.UpdateOrderStatusRequest;
import com.shopMeta.order_service.entity.OrderStatus;
import com.shopMeta.order_service.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Admin view over every user's orders (spec 11). The ADMIN role is enforced by the security rule
 * on {@code /api/v1/admin/**}; these endpoints are also not reachable without a JWT at all.
 */
@Tag(name = "Orders (admin)", description = "Every user's orders and the fulfilment lifecycle")
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @Operation(summary = "List every user's orders",
            description = "Paged, newest first, optionally filtered by status.")
    @GetMapping
    public ResponseEntity<OrderPageResponse> listOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        return ResponseEntity.ok(orderService.listAllOrders(status, page, size));
    }

    @Operation(summary = "Move an order through fulfilment",
            description = "CONFIRMED → SHIPPED → DELIVERED, or CANCELLED while still CREATED "
                    + "(releases the stock). Orders are confirmed by their payment, not by an "
                    + "admin, and CANCELLED/DELIVERED are terminal — anything else is a 422.")
    @PostMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {

        return ResponseEntity.ok(orderService.updateStatus(id, request.getStatus()));
    }
}
