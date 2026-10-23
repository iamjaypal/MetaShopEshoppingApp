package com.shopMeta.cart_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopMeta.cart_service.service.CartService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Service-to-service cart operations (spec 10.3).
 *
 * <p>Not reachable through the API Gateway (see D-041); callers hit the service port directly and
 * must send {@code X-Internal-Token}. Responses are plain JSON — no {@code ApiResponse} envelope —
 * so the Feign clients stay trivial (D-022).
 */
@Tag(name = "Cart (internal)", description = "Service-to-service cart operations")
@RestController
@RequestMapping("/api/v1/internal/cart")
@RequiredArgsConstructor
public class InternalCartController {

    private final CartService cartService;

    /**
     * Empties a user's cart once their order is confirmed (spec 10.3). Idempotent, so a replayed
     * confirmation is harmless.
     */
    @Operation(summary = "Clear a user's cart after their order was confirmed",
            description = "Requires X-Internal-Token. Idempotent: a user with no cart, or an "
                    + "already empty cart, returns 204 as well.")
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> clearCart(@PathVariable Long userId) {

        cartService.clearCartForUser(userId);

        return ResponseEntity.noContent().build();
    }
}
