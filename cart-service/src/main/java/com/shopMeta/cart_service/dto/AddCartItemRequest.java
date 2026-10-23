package com.shopMeta.cart_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of {@code POST /api/v1/cart/items} (spec 10.3). There is no user id here on purpose: the
 * cart always belongs to the authenticated caller (spec 10.3 / 18).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddCartItemRequest {

    @Schema(description = "Id of an active product", example = "1")
    @NotNull(message = "productId is required")
    private Long productId;

    @Schema(description = "How many to add; 1..20 in total per product", example = "2")
    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    @Max(value = 20, message = "quantity must be at most 20")
    private Integer quantity;
}
