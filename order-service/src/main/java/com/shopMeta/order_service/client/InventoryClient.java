package com.shopMeta.order_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.shopMeta.order_service.client.dto.OrderReserveRequest;
import com.shopMeta.order_service.client.dto.OrderReservationRequest;
import com.shopMeta.order_service.client.dto.ReservationResponse;

/**
 * inventory-service's internal reservation API (spec 9.3).
 *
 * <ul>
 *   <li>{@code reserve} is all-or-nothing and idempotent per {@code orderId} (D-053) — a 409 names
 *       the lines that cannot be satisfied, a replay returns the existing reservation.</li>
 *   <li>{@code confirm} turns the holds into sales once payment succeeds.</li>
 *   <li>{@code release} gives the holds back after a failure or a cancellation.</li>
 * </ul>
 */
@FeignClient(name = "inventory-service")
public interface InventoryClient {

    @PostMapping("/api/v1/internal/inventory/reserve")
    ReservationResponse reserve(@RequestBody OrderReserveRequest request);

    @PostMapping("/api/v1/internal/inventory/confirm")
    ReservationResponse confirm(@RequestBody OrderReservationRequest request);

    @PostMapping("/api/v1/internal/inventory/release")
    ReservationResponse release(@RequestBody OrderReservationRequest request);
}
