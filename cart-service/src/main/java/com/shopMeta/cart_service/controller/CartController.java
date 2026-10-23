package com.shopMeta.cart_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopMeta.cart_service.dto.AddCartItemRequest;
import com.shopMeta.cart_service.dto.CartResponse;
import com.shopMeta.cart_service.dto.CartValidationResponse;
import com.shopMeta.cart_service.dto.UpdateCartItemRequest;
import com.shopMeta.cart_service.exception.UnauthenticatedException;
import com.shopMeta.cart_service.security.AuthenticatedUser;
import com.shopMeta.cart_service.service.CartService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The signed-in user's cart (spec 10.3).
 *
 * <p>Every endpoint returns the whole cart after a change, so the Angular cart page and its badge
 * can refresh from one round trip. The cart belongs to the token subject — there is no user id in
 * any request body (spec 10.3 / 18).
 */
@Tag(name = "Cart", description = "The signed-in user's shopping cart")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /** Adds a product, or increases the quantity of an existing line. */
    @Operation(summary = "Add a product to the cart (or increase its quantity)",
            description = "Validates that the product exists, is active and is in stock for the "
                    + "resulting quantity. 404 unknown product, 422 inactive or over the per-item "
                    + "limit, 409 not enough stock.")
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AddCartItemRequest request) {

        return ResponseEntity.ok(cartService.addItem(requireUserId(user), request));
    }

    /** The cart with live prices, line totals, subtotal and stock warnings. */
    @Operation(summary = "Get the cart with live prices and stock warnings",
            description = "Calls product-service for the current price and inventory-service for "
                    + "the current stock. 503 when either is unreachable, rather than showing a "
                    + "stale price.")
    @GetMapping
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal AuthenticatedUser user) {

        return ResponseEntity.ok(cartService.getCart(requireUserId(user)));
    }

    /** Changes a line to an absolute quantity. */
    @Operation(summary = "Change the quantity of a cart line",
            description = "Absolute quantity, 1..20. 404 when the line is not in your cart.")
    @PutMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> updateItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {

        return ResponseEntity.ok(cartService.updateItem(requireUserId(user), itemId, request));
    }

    /** Removes one line. */
    @Operation(summary = "Remove one line from the cart",
            description = "404 when the line is not in your cart. Returns the updated cart.")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> removeItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long itemId) {

        return ResponseEntity.ok(cartService.removeItem(requireUserId(user), itemId));
    }

    /** Empties the cart. */
    @Operation(summary = "Empty the cart",
            description = "Keeps the (now empty) cart so the next add is cheap. Idempotent.")
    @DeleteMapping
    public ResponseEntity<CartResponse> clearCart(@AuthenticationPrincipal AuthenticatedUser user) {

        return ResponseEntity.ok(cartService.clearCart(requireUserId(user)));
    }

    /** Pre-checkout validation. */
    @Operation(summary = "Pre-checkout validation",
            description = "Reports every line that is inactive, out of stock or over the quantity "
                    + "limit. Always 200; an invalid cart is an answer, not an error.")
    @PostMapping("/validate")
    public ResponseEntity<CartValidationResponse> validate(
            @AuthenticationPrincipal AuthenticatedUser user) {

        return ResponseEntity.ok(cartService.validate(requireUserId(user)));
    }

    /**
     * The cart always belongs to the token subject. A token without a usable subject cannot be
     * mapped to a cart, so it is rejected rather than guessed at.
     */
    private static Long requireUserId(AuthenticatedUser user) {

        if (user == null || user.userId() == null) {
            throw new UnauthenticatedException(
                    "The access token does not identify a user");
        }
        return user.userId();
    }
}
