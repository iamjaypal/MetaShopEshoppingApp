package com.shopMeta.order_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopMeta.order_service.dto.CreateOrderRequest;
import com.shopMeta.order_service.dto.OrderPageResponse;
import com.shopMeta.order_service.dto.OrderResponse;
import com.shopMeta.order_service.exception.UnauthenticatedException;
import com.shopMeta.order_service.security.AuthenticatedUser;
import com.shopMeta.order_service.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The customer's orders (spec 11). The caller's identity comes from the token, never from the
 * request — there is no way to create an order for someone else or to read one.
 */
@Tag(name = "Orders", description = "Checkout and the caller's order history")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** Checkout: turns the caller's cart into an order and holds the stock. */
    @Operation(summary = "Checkout: create an order from your cart",
            description = "Reads your cart (through your own token), freezes prices and the "
                    + "shipping address, reserves stock, returns 201 with the order in CREATED. "
                    + "422 empty cart / unavailable items, 409 blocked checkout or out of stock, "
                    + "503 when a dependency is down.")
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateOrderRequest request) {

        OrderResponse order = orderService.createOrder(requireUserId(user), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /** The caller's orders, newest first. */
    @Operation(summary = "List your orders",
            description = "Paged, newest first.")
    @GetMapping
    public ResponseEntity<OrderPageResponse> listMyOrders(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        return ResponseEntity.ok(orderService.listMyOrders(requireUserId(user), page, size));
    }

    /** One order — the owner, or an admin. */
    @Operation(summary = "Get one order",
            description = "Owners see their own orders, admins any order. A customer asking for "
                    + "someone else's order gets the same 404 as a missing one.")
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {

        return ResponseEntity.ok(orderService.getOrder(
                requireUserId(user), id, user != null && user.isAdmin()));
    }

    /** Cancels an order that has not been paid yet, releasing its stock. */
    @Operation(summary = "Cancel your order",
            description = "Allowed while the order is CREATED (unpaid, stock still held). The "
                    + "stock is released back to inventory. Paid orders are refunded through "
                    + "payment-service instead.")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {

        return ResponseEntity.ok(orderService.cancelByOwner(requireUserId(user), id));
    }

    /** The cart and the order both belong to the token subject. */
    private static Long requireUserId(AuthenticatedUser user) {

        if (user == null || user.userId() == null) {
            throw new UnauthenticatedException("The access token does not identify a user");
        }
        return user.userId();
    }
}
