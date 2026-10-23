package com.shopMeta.inventory_service.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopMeta.inventory_service.config.InventoryProperties;
import com.shopMeta.inventory_service.dto.AvailabilityBatchResponse;
import com.shopMeta.inventory_service.dto.AvailabilityItemRequest;
import com.shopMeta.inventory_service.dto.AvailabilityItemResponse;
import com.shopMeta.inventory_service.dto.AvailabilityRequest;
import com.shopMeta.inventory_service.dto.ReservationItemRequest;
import com.shopMeta.inventory_service.dto.ReservationItemResponse;
import com.shopMeta.inventory_service.dto.ReservationResponse;
import com.shopMeta.inventory_service.dto.ReserveRequest;
import com.shopMeta.inventory_service.dto.UnavailableItem;
import com.shopMeta.inventory_service.entity.Inventory;
import com.shopMeta.inventory_service.entity.InventoryReservation;
import com.shopMeta.inventory_service.entity.ReservationStatus;
import com.shopMeta.inventory_service.entity.StockMovementReason;
import com.shopMeta.inventory_service.exception.BusinessRuleException;
import com.shopMeta.inventory_service.exception.InsufficientStockException;
import com.shopMeta.inventory_service.exception.ResourceNotFoundException;
import com.shopMeta.inventory_service.repository.InventoryRepository;
import com.shopMeta.inventory_service.repository.InventoryReservationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The reservation lifecycle (spec 9.3 / 9.4) — the only place stock is held for an order.
 *
 * <h2>How overselling is prevented</h2>
 * {@code reserve} never reads a quantity and then writes it back. For each line it issues a single
 * conditional statement
 * ({@link InventoryRepository#reserve}) that decrements the row only when it still holds enough
 * stock, and reports how many rows it changed. Two transactions racing for the last unit both run the
 * same statement; the database serialises them, the second finds too little stock and updates zero
 * rows. The reservation is then rejected.
 *
 * <p>The whole order is one transaction, so a failure on the third line rolls the first two back —
 * reserving is genuinely all-or-nothing. Lines are processed in ascending product id order so two
 * concurrent orders touching the same products cannot deadlock against each other.
 *
 * <p>Every operation is idempotent per {@code orderId}: a retried {@code reserve} returns the
 * existing reservations instead of holding stock twice, and a repeated {@code release} is a no-op.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final StockMovementRecorder stockMovementRecorder;
    private final InventoryProperties properties;

    // ------------------------------------------------------------------ reserve

    /**
     * Holds stock for an order, all-or-nothing (spec 9.3, {@code POST /internal/inventory/reserve}).
     *
     * @throws InsufficientStockException 409 listing every line that cannot be satisfied
     */
    @Transactional
    public ReservationResponse reserve(ReserveRequest request) {

        Long orderId = request.getOrderId();

        List<InventoryReservation> existing =
                reservationRepository.findByOrderIdOrderByProductIdAsc(orderId);

        if (!existing.isEmpty()) {
            log.info("Reserve for order {} is a replay; returning the {} existing reservation(s)",
                    orderId, existing.size());
            return toResponse(orderId, existing);
        }

        List<ReservationItemRequest> items = mergeDuplicateProducts(request.getItems());

        // Ascending product id: two concurrent orders lock rows in the same order, so neither can
        // hold a row the other needs while waiting for a second one.
        List<ReservationItemRequest> sorted = items.stream()
                .sorted(Comparator.comparing(ReservationItemRequest::getProductId))
                .toList();

        // Fail fast with the complete list of unavailable lines, so order-service can tell the
        // customer everything that is missing in one response rather than one product at a time.
        rejectIfUnavailable(sorted);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(properties.getReservationTtl());

        List<InventoryReservation> reservations = new ArrayList<>(sorted.size());

        for (ReservationItemRequest item : sorted) {

            int updated = inventoryRepository.reserve(item.getProductId(), item.getQuantity(), now);

            if (updated == 0) {
                // Another order took the stock between the check above and this statement. The
                // transaction rolls back, so nothing is held.
                int available = currentAvailable(item.getProductId());
                throw new InsufficientStockException(
                        "Insufficient stock for product " + item.getProductId(),
                        List.of(unavailable(item, available)));
            }

            reservations.add(reservationRepository.save(InventoryReservation.builder()
                    .orderId(orderId)
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .status(ReservationStatus.RESERVED)
                    .expiresAt(expiresAt)
                    .build()));

            stockMovementRecorder.record(item.getProductId(), -item.getQuantity(),
                    StockMovementReason.RESERVE, "order:" + orderId);
        }

        log.info("Reserved {} line(s) for order {} (expires {})", reservations.size(), orderId, expiresAt);

        return toResponse(orderId, reservations);
    }

    // ------------------------------------------------------------------ release

    /**
     * Returns every held line of an order to available stock (spec 9.3,
     * {@code POST /internal/inventory/release}) — payment failed, or the order was cancelled.
     *
     * <p>Idempotent: lines that are already RELEASED or EXPIRED are left alone.
     */
    @Transactional
    public ReservationResponse release(Long orderId) {

        List<InventoryReservation> reservations = requireReservations(orderId);

        if (reservations.stream().anyMatch(r -> r.getStatus() == ReservationStatus.CONFIRMED)) {
            throw new BusinessRuleException("Reservations for order " + orderId
                    + " are already confirmed and cannot be released");
        }

        LocalDateTime now = LocalDateTime.now();

        for (InventoryReservation reservation : reservations) {

            if (reservation.getStatus() != ReservationStatus.RESERVED) {
                continue; // already RELEASED or EXPIRED — a replay
            }

            int updated = inventoryRepository.release(
                    reservation.getProductId(), reservation.getQuantity(), now);

            if (updated == 0) {
                throw new BusinessRuleException("Reserved stock for product "
                        + reservation.getProductId() + " could not be found, so the reservation "
                        + "cannot be released");
            }

            reservation.setStatus(ReservationStatus.RELEASED);
            stockMovementRecorder.record(reservation.getProductId(), reservation.getQuantity(),
                    StockMovementReason.RELEASE, "order:" + orderId);
        }

        reservationRepository.saveAll(reservations);
        log.info("Released order {}", orderId);

        return toResponse(orderId, reservations);
    }

    // ------------------------------------------------------------------ confirm

    /**
     * Turns the holds of an order into sales (spec 9.3, {@code POST /internal/inventory/confirm}) —
     * payment succeeded. The units leave reserved stock for good.
     *
     * <p>Idempotent: already-CONFIRMED lines are left alone. A line that was released or expired can
     * no longer be confirmed and returns 422.
     */
    @Transactional
    public ReservationResponse confirm(Long orderId) {

        List<InventoryReservation> reservations = requireReservations(orderId);
        LocalDateTime now = LocalDateTime.now();

        for (InventoryReservation reservation : reservations) {

            switch (reservation.getStatus()) {

                case RESERVED -> {
                    int updated = inventoryRepository.confirm(
                            reservation.getProductId(), reservation.getQuantity(), now);

                    if (updated == 0) {
                        throw new BusinessRuleException("Reserved stock for product "
                                + reservation.getProductId() + " could not be found, so the "
                                + "reservation cannot be confirmed");
                    }

                    reservation.setStatus(ReservationStatus.CONFIRMED);
                    // The units were already out of available stock, so the available delta is 0.
                    stockMovementRecorder.record(reservation.getProductId(), 0,
                            StockMovementReason.CONFIRM, "order:" + orderId);
                }

                case CONFIRMED -> {
                    // Replay — nothing to do.
                }

                case RELEASED, EXPIRED -> throw new BusinessRuleException("Reservation for product "
                        + reservation.getProductId() + " was "
                        + reservation.getStatus().name().toLowerCase()
                        + " and can no longer be confirmed");
            }
        }

        reservationRepository.saveAll(reservations);
        log.info("Confirmed order {}", orderId);

        return toResponse(orderId, reservations);
    }

    // ------------------------------------------------------------------ availability

    /**
     * Bulk availability check for cart and order validation (spec 9.3,
     * {@code POST /internal/inventory/availability}). Internal callers see the exact quantity, not
     * the capped public one.
     */
    @Transactional(readOnly = true)
    public AvailabilityBatchResponse checkAvailability(AvailabilityRequest request) {

        List<AvailabilityItemResponse> items = request.getItems().stream()
                .map(this::toAvailabilityItem)
                .toList();

        return AvailabilityBatchResponse.builder()
                .items(items)
                .allAvailable(items.stream().allMatch(AvailabilityItemResponse::isSufficient))
                .build();
    }

    // ------------------------------------------------------------------ expiry

    /**
     * Releases reservations that were never confirmed or released in time (spec 9.4). Called by
     * {@link ReservationExpirySweeper} on a schedule.
     *
     * @return how many reservations were expired
     */
    @Transactional
    public int expireOverdueReservations() {

        List<InventoryReservation> overdue = reservationRepository
                .findByStatusAndExpiresAtBefore(ReservationStatus.RESERVED, LocalDateTime.now());

        if (overdue.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        int expiredCount = 0;

        for (InventoryReservation reservation : overdue) {

            int updated = inventoryRepository.release(
                    reservation.getProductId(), reservation.getQuantity(), now);

            if (updated == 0) {
                // The counters no longer match the reservation; leave it RESERVED and try again on
                // the next sweep rather than marking it expired without returning the stock.
                log.warn("Could not return reserved stock for product {} (order {}); leaving the "
                                + "reservation RESERVED", reservation.getProductId(),
                        reservation.getOrderId());
                continue;
            }

            reservation.setStatus(ReservationStatus.EXPIRED);
            expiredCount++;
            stockMovementRecorder.record(reservation.getProductId(), reservation.getQuantity(),
                    StockMovementReason.EXPIRE, "order:" + reservation.getOrderId());
        }

        reservationRepository.saveAll(overdue);

        if (expiredCount > 0) {
            log.info("Expired {} reservation(s) that were not confirmed in time", expiredCount);
        }

        return expiredCount;
    }

    // ------------------------------------------------------------------ helpers

    private List<InventoryReservation> requireReservations(Long orderId) {

        List<InventoryReservation> reservations =
                reservationRepository.findByOrderIdOrderByProductIdAsc(orderId);

        if (reservations.isEmpty()) {
            throw new ResourceNotFoundException("No reservations found for order " + orderId);
        }

        return reservations;
    }

    /** Collapses repeated product ids into one line so the unique key is never violated. */
    private List<ReservationItemRequest> mergeDuplicateProducts(List<ReservationItemRequest> items) {

        Map<Long, Integer> merged = new LinkedHashMap<>();

        for (ReservationItemRequest item : items) {
            merged.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        return merged.entrySet().stream()
                .map(entry -> ReservationItemRequest.builder()
                        .productId(entry.getKey())
                        .quantity(entry.getValue())
                        .build())
                .toList();
    }

    private void rejectIfUnavailable(List<ReservationItemRequest> items) {

        List<UnavailableItem> unavailable = new ArrayList<>();

        for (ReservationItemRequest item : items) {
            int available = currentAvailable(item.getProductId());
            if (available < item.getQuantity()) {
                unavailable.add(unavailable(item, available));
            }
        }

        if (!unavailable.isEmpty()) {
            throw new InsufficientStockException(
                    "Insufficient stock for " + unavailable.size() + " item(s)", unavailable);
        }
    }

    private UnavailableItem unavailable(ReservationItemRequest item, int available) {

        return UnavailableItem.builder()
                .productId(item.getProductId())
                .requested(item.getQuantity())
                .available(available)
                .build();
    }

    /** Sellable units, or 0 when the product has no stock row yet. */
    private int currentAvailable(Long productId) {

        return inventoryRepository.findByProductId(productId)
                .map(Inventory::getAvailableQuantity)
                .orElse(0);
    }

    private AvailabilityItemResponse toAvailabilityItem(AvailabilityItemRequest item) {

        int requested = item.getQuantity() != null ? item.getQuantity() : 1;

        Inventory stock = inventoryRepository.findByProductId(item.getProductId()).orElse(null);
        int available = stock == null ? 0 : stock.getAvailableQuantity();

        return AvailabilityItemResponse.builder()
                .productId(item.getProductId())
                .sku(stock == null ? null : stock.getSku())
                .available(available)
                .requested(requested)
                .sufficient(available >= requested)
                .inStock(available > 0)
                .build();
    }

    private ReservationResponse toResponse(Long orderId, List<InventoryReservation> reservations) {

        List<ReservationItemResponse> items = reservations.stream()
                .map(reservation -> ReservationItemResponse.builder()
                        .productId(reservation.getProductId())
                        .quantity(reservation.getQuantity())
                        .status(reservation.getStatus().name())
                        .expiresAt(reservation.getExpiresAt())
                        .build())
                .toList();

        return ReservationResponse.builder()
                .orderId(orderId)
                .status(overallStatus(reservations))
                .items(items)
                .build();
    }

    /** The single status when every line agrees, otherwise {@code MIXED}. */
    private String overallStatus(List<InventoryReservation> reservations) {

        Set<ReservationStatus> statuses = reservations.stream()
                .map(InventoryReservation::getStatus)
                .collect(Collectors.toSet());

        return statuses.size() == 1 ? statuses.iterator().next().name() : "MIXED";
    }
}
