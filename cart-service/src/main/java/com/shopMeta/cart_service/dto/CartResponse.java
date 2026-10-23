package com.shopMeta.cart_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The whole cart, as returned by every cart endpoint (spec 10.3).
 *
 * <p>{@code subtotal} is computed from live prices, so it is an estimate of what the cart would cost
 * right now — not a quote. The authoritative amount is the order snapshot (spec 10.2).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartResponse {

    private Long cartId;

    private Long userId;

    private List<CartItemResponse> items;

    /** Sum of all quantities. */
    private int totalItems;

    /** Number of distinct products. */
    private int distinctItems;

    private BigDecimal subtotal;

    @Schema(example = "INR")
    private String currency;

    private boolean hasWarnings;

    /** One entry per problematic line, for a quick banner in the UI. */
    private List<String> warnings;

    private LocalDateTime updatedAt;

    /** A cart that exists but holds nothing — still 200, never a 404. */
    public static CartResponse empty(Long userId, Long cartId) {
        return CartResponse.builder()
                .cartId(cartId)
                .userId(userId)
                .items(List.of())
                .totalItems(0)
                .distinctItems(0)
                .subtotal(BigDecimal.ZERO)
                .currency("INR")
                .hasWarnings(false)
                .warnings(List.of())
                .build();
    }
}
