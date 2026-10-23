package com.shopMeta.inventory_service.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shopMeta.inventory_service.entity.InventoryReservation;
import com.shopMeta.inventory_service.entity.ReservationStatus;

/**
 * Reservation lines (spec 9.2). Every reservation belongs to an order, so the order id is the unit
 * of work for reserve / release / confirm.
 */
@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    List<InventoryReservation> findByOrderIdOrderByProductIdAsc(Long orderId);

    List<InventoryReservation> findByOrderIdAndStatus(Long orderId, ReservationStatus status);

    Optional<InventoryReservation> findByOrderIdAndProductId(Long orderId, Long productId);

    /** Rows the expiry sweeper must release (spec 9.4). */
    List<InventoryReservation> findByStatusAndExpiresAtBefore(
            ReservationStatus status, LocalDateTime cutoff);

    long countByProductIdAndStatus(Long productId, ReservationStatus status);
}
