package com.shopMeta.order_service.client.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirror of inventory-service's {@code ReservationResponse} (spec 9.3): the state the order's
 * reservations now have after a reserve / confirm / release.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReservationResponse {

    private Long orderId;

    /** {@code RESERVED}, {@code CONFIRMED}, {@code RELEASED} or {@code EXPIRED}. */
    private String status;

    private List<ReservationItemView> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReservationItemView {

        private Long productId;

        private int quantity;

        private String status;

        private LocalDateTime expiresAt;
    }
}
