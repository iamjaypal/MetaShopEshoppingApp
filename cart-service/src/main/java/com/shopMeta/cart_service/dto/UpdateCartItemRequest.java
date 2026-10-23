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
 * Body of {@code PUT /api/v1/cart/items/{itemId}} (spec 10.3). The quantity is absolute, not a
 * delta; removing a line is {@code DELETE}, so 0 is rejected here.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCartItemRequest {

    @Schema(description = "New absolute quantity; 1..20", example = "3")
    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    @Max(value = 20, message = "quantity must be at most 20")
    private Integer quantity;
}
