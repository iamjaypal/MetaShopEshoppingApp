package com.shopMeta.order_service.client.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirror of inventory-service's {@code ReserveRequest} (spec 9.3,
 * {@code POST /api/v1/internal/inventory/reserve}): hold stock for all lines of one order,
 * all-or-nothing.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderReserveRequest {

    @NotNull(message = "orderId is required")
    private Long orderId;

    @NotEmpty(message = "items must not be empty")
    @Size(max = 100, message = "at most 100 items can be reserved at once")
    @Valid
    private List<ReservationLine> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReservationLine {

        @NotNull(message = "productId is required")
        private Long productId;

        private Integer quantity;
    }

    /** Convenience factory used by the checkout flow. */
    public static OrderReserveRequest of(Long orderId, Map<Long, Integer> quantityByProductId) {

        List<ReservationLine> lines = quantityByProductId.entrySet().stream()
                .map(entry -> new ReservationLine(entry.getKey(), entry.getValue()))
                .toList();
        return new OrderReserveRequest(orderId, lines);
    }
}
