package com.shopMeta.inventory_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopMeta.inventory_service.config.InventoryProperties;
import com.shopMeta.inventory_service.dto.AvailabilityBatchResponse;
import com.shopMeta.inventory_service.dto.AvailabilityItemRequest;
import com.shopMeta.inventory_service.dto.AvailabilityRequest;
import com.shopMeta.inventory_service.dto.ReservationItemRequest;
import com.shopMeta.inventory_service.dto.ReservationResponse;
import com.shopMeta.inventory_service.dto.ReserveRequest;
import com.shopMeta.inventory_service.entity.Inventory;
import com.shopMeta.inventory_service.entity.InventoryReservation;
import com.shopMeta.inventory_service.entity.ReservationStatus;
import com.shopMeta.inventory_service.entity.StockMovement;
import com.shopMeta.inventory_service.entity.StockMovementReason;
import com.shopMeta.inventory_service.exception.BusinessRuleException;
import com.shopMeta.inventory_service.exception.InsufficientStockException;
import com.shopMeta.inventory_service.exception.ResourceNotFoundException;
import com.shopMeta.inventory_service.repository.InventoryRepository;
import com.shopMeta.inventory_service.repository.InventoryReservationRepository;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryReservationRepository reservationRepository;

    @Mock
    private StockMovementRecorder stockMovementRecorder;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
                inventoryRepository,
                reservationRepository,
                stockMovementRecorder,
                new InventoryProperties());
    }

    // ------------------------------------------------------------------ fixtures

    private Inventory stock(long productId, int available) {
        return Inventory.builder()
                .id(productId)
                .productId(productId)
                .sku("SKU-" + productId)
                .availableQuantity(available)
                .reservedQuantity(0)
                .lowStockThreshold(5)
                .build();
    }

    private InventoryReservation reservation(
            long orderId, long productId, int quantity, ReservationStatus status) {

        return InventoryReservation.builder()
                .id(productId)
                .orderId(orderId)
                .productId(productId)
                .quantity(quantity)
                .status(status)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();
    }

    private ReserveRequest reserveRequest(long orderId, ReservationItemRequest... items) {
        return ReserveRequest.builder().orderId(orderId).items(List.of(items)).build();
    }

    private ReservationItemRequest item(long productId, int quantity) {
        return ReservationItemRequest.builder().productId(productId).quantity(quantity).build();
    }

    private void stockExists(Inventory... stocks) {
        for (Inventory s : stocks) {
            given(inventoryRepository.findByProductId(s.getProductId())).willReturn(Optional.of(s));
        }
    }

    private void reserveSucceeds(int... productIds) {
        for (int productId : productIds) {
            given(inventoryRepository.reserve(eq((long) productId), anyInt(), any()))
                    .willReturn(1);
        }
        given(reservationRepository.save(any(InventoryReservation.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    // ------------------------------------------------------------------ reserve

    @Test
    @DisplayName("reserve: holds every line and records a RESERVE movement for each")
    void reserve_holdsStockAndLogsMovements() {

        given(reservationRepository.findByOrderIdOrderByProductIdAsc(500L)).willReturn(List.of());
        stockExists(stock(1, 10), stock(2, 5));
        reserveSucceeds(1, 2);

        ReservationResponse response = reservationService.reserve(
                reserveRequest(500L, item(1, 2), item(2, 3)));

        assertThat(response.getOrderId()).isEqualTo(500L);
        assertThat(response.getStatus()).isEqualTo("RESERVED");
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems())
                .extracting("productId", "quantity", "status")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, 2, "RESERVED"),
                        org.assertj.core.groups.Tuple.tuple(2L, 3, "RESERVED"));

        then(stockMovementRecorder).should(org.mockito.Mockito.times(2))
                .record(anyLong(), anyInt(), eq(StockMovementReason.RESERVE), eq("order:500"));

        then(reservationRepository).should(org.mockito.Mockito.times(2))
                .save(any(InventoryReservation.class));
    }

    @Test
    @DisplayName("reserve: processes lines in ascending product id order to avoid deadlocks")
    void reserve_processesLinesInAscendingProductIdOrder() {

        given(reservationRepository.findByOrderIdOrderByProductIdAsc(500L)).willReturn(List.of());
        stockExists(stock(3, 10), stock(1, 10), stock(2, 10));
        reserveSucceeds(1, 2, 3);

        reservationService.reserve(reserveRequest(500L, item(3, 1), item(1, 1), item(2, 1)));

        InOrder inOrder = inOrder(inventoryRepository);
        inOrder.verify(inventoryRepository).reserve(eq(1L), anyInt(), any());
        inOrder.verify(inventoryRepository).reserve(eq(2L), anyInt(), any());
        inOrder.verify(inventoryRepository).reserve(eq(3L), anyInt(), any());
    }

    @Test
    @DisplayName("reserve: is idempotent per orderId — a replay returns the existing reservations")
    void reserve_replayReturnsExistingReservations() {

        given(reservationRepository.findByOrderIdOrderByProductIdAsc(500L))
                .willReturn(List.of(reservation(500L, 1L, 2, ReservationStatus.RESERVED)));

        ReservationResponse response = reservationService.reserve(reserveRequest(500L, item(1, 2)));

        assertThat(response.getStatus()).isEqualTo("RESERVED");
        then(inventoryRepository).should(never()).reserve(anyLong(), anyInt(), any());
        then(reservationRepository).should(never()).save(any(InventoryReservation.class));
    }

    @Test
    @DisplayName("reserve: rejects the whole order with 409 when a line is short, holding nothing")
    void reserve_rejectsAllWhenAnyLineIsUnavailable() {

        given(reservationRepository.findByOrderIdOrderByProductIdAsc(500L)).willReturn(List.of());
        stockExists(stock(1, 10), stock(2, 1));

        assertThatThrownBy(() -> reservationService.reserve(
                reserveRequest(500L, item(1, 2), item(2, 5))))
                .isInstanceOf(InsufficientStockException.class)
                .satisfies(thrown -> assertThat(
                        ((InsufficientStockException) thrown).getUnavailableItems())
                        .singleElement()
                        .satisfies(entry -> {
                            assertThat(entry.getProductId()).isEqualTo(2L);
                            assertThat(entry.getRequested()).isEqualTo(5);
                            assertThat(entry.getAvailable()).isEqualTo(1);
                        }));

        // Nothing at all was held — the order is all-or-nothing.
        then(inventoryRepository).should(never()).reserve(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("reserve: a product with no stock row at all counts as zero available")
    void reserve_treatsMissingStockRowAsUnavailable() {

        given(reservationRepository.findByOrderIdOrderByProductIdAsc(500L)).willReturn(List.of());
        given(inventoryRepository.findByProductId(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.reserve(reserveRequest(500L, item(9, 1))))
                .isInstanceOf(InsufficientStockException.class)
                .satisfies(thrown -> assertThat(
                        ((InsufficientStockException) thrown).getUnavailableItems())
                        .singleElement()
                        .satisfies(entry -> {
                            assertThat(entry.getAvailable()).isZero();
                        }));
    }

    @Test
    @DisplayName("reserve: repeated product ids are merged into one line")
    void reserve_mergesDuplicateProductIds() {

        given(reservationRepository.findByOrderIdOrderByProductIdAsc(500L)).willReturn(List.of());
        stockExists(stock(1, 10));
        reserveSucceeds(1);

        reservationService.reserve(reserveRequest(500L, item(1, 2), item(1, 3)));

        then(inventoryRepository).should(org.mockito.Mockito.times(1))
                .reserve(eq(1L), eq(5), any());
        then(reservationRepository).should(org.mockito.Mockito.times(1))
                .save(any(InventoryReservation.class));
    }

    @Test
    @DisplayName("reserve: loses the race for the last unit and reports 409")
    void reserve_reportsConflictWhenTheConditionalUpdateChangesNoRows() {

        given(reservationRepository.findByOrderIdOrderByProductIdAsc(500L)).willReturn(List.of());
        // The pre-check sees stock, but another order takes it before the update runs, so the read
        // used to build the 409 body reports zero.
        given(inventoryRepository.findByProductId(1L))
                .willReturn(Optional.of(stock(1, 10)), Optional.of(stock(1, 0)));
        given(inventoryRepository.reserve(eq(1L), anyInt(), any())).willReturn(0);

        assertThatThrownBy(() -> reservationService.reserve(reserveRequest(500L, item(1, 2))))
                .isInstanceOf(InsufficientStockException.class);

        then(reservationRepository).should(never()).save(any(InventoryReservation.class));
    }

    // ------------------------------------------------------------------ release

    @Test
    @DisplayName("release: returns every RESERVED line to available stock")
    void release_returnsReservedStock() {

        given(reservationRepository.findByOrderIdOrderByProductIdAsc(500L))
                .willReturn(List.of(reservation(500L, 1L, 2, ReservationStatus.RESERVED)));
        given(inventoryRepository.release(eq(1L), eq(2), any())).willReturn(1);

        ReservationResponse response = reservationService.release(500L);

        assertThat(response.getStatus()).isEqualTo("RELEASED");
        then(stockMovementRecorder).should()
                .record(1L, 2, StockMovementReason.RELEASE, "order:500");
    }

    @Test
    @DisplayName("release: is idempotent — an already RELEASED order is a no-op success")
    void release_isIdempotent() {

        given(reservationRepository.findByOrderIdOrderByProductIdAsc(500L))
                .willReturn(List.of(reservation(500L, 1L, 2, ReservationStatus.RELEASED)));

        ReservationResponse response = reservationService.release(500L);

        assertThat(response.getStatus()).isEqualTo("RELEASED");
        then(inventoryRepository).should(never()).release(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("release: refuses to release an order that was already confirmed")
    void release_rejectsConfirmedOrder() {

        given(reservationRepository.findByOrderIdOrderByProductIdAsc(500L))
                .willReturn(List.of(reservation(500L, 1L, 2, ReservationStatus.CONFIRMED)));

        assertThatThrownBy(() -> reservationService.release(500L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already confirmed");
    }

    @Test
    @DisplayName("release: an unknown order is a 404")
    void release_unknownOrderIsNotFound() {

        given(reservationRepository.findByOrderIdOrderByProductIdAsc(999L)).willReturn(List.of());

        assertThatThrownBy(() -> reservationService.release(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------ confirm

    @Test
    @DisplayName("confirm: turns the hold into a sale and logs a CONFIRM movement")
    void confirm_turnsHoldIntoSale() {

        given(reservationRepository.findByOrderIdOrderByProductIdAsc(500L))
                .willReturn(List.of(reservation(500L, 1L, 2, ReservationStatus.RESERVED)));
        given(inventoryRepository.confirm(eq(1L), eq(2), any())).willReturn(1);

        ReservationResponse response = reservationService.confirm(500L);

        assertThat(response.getStatus()).isEqualTo("CONFIRMED");
        then(stockMovementRecorder).should()
                .record(1L, 0, StockMovementReason.CONFIRM, "order:500");
    }

    @Test
    @DisplayName("confirm: is idempotent — an already CONFIRMED order is a no-op success")
    void confirm_isIdempotent() {

        given(reservationRepository.findByOrderIdOrderByProductIdAsc(500L))
                .willReturn(List.of(reservation(500L, 1L, 2, ReservationStatus.CONFIRMED)));

        ReservationResponse response = reservationService.confirm(500L);

        assertThat(response.getStatus()).isEqualTo("CONFIRMED");
        then(inventoryRepository).should(never()).confirm(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("confirm: a released or expired reservation can no longer be confirmed")
    void confirm_rejectsReleasedReservation() {

        given(reservationRepository.findByOrderIdOrderByProductIdAsc(500L))
                .willReturn(List.of(reservation(500L, 1L, 2, ReservationStatus.EXPIRED)));

        assertThatThrownBy(() -> reservationService.confirm(500L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");
    }

    // ------------------------------------------------------------------ availability

    @Test
    @DisplayName("availability: reports exact quantities and the sufficient flag")
    void availability_reportsExactQuantities() {

        stockExists(stock(1, 10), stock(2, 1));

        AvailabilityBatchResponse response = reservationService.checkAvailability(
                AvailabilityRequest.builder()
                        .items(List.of(
                                AvailabilityItemRequest.builder().productId(1L).quantity(5).build(),
                                AvailabilityItemRequest.builder().productId(2L).quantity(3).build()))
                        .build());

        assertThat(response.isAllAvailable()).isFalse();
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems().get(0).getAvailable()).isEqualTo(10);
        assertThat(response.getItems().get(0).isSufficient()).isTrue();
        assertThat(response.getItems().get(1).getAvailable()).isEqualTo(1);
        assertThat(response.getItems().get(1).isSufficient()).isFalse();
    }

    @Test
    @DisplayName("availability: quantity defaults to 1 and an unknown product reports zero")
    void availability_defaultsQuantityAndHandlesUnknownProduct() {

        given(inventoryRepository.findByProductId(1L)).willReturn(Optional.of(stock(1, 4)));
        given(inventoryRepository.findByProductId(7L)).willReturn(Optional.empty());

        AvailabilityBatchResponse response = reservationService.checkAvailability(
                AvailabilityRequest.builder()
                        .items(List.of(
                                AvailabilityItemRequest.builder().productId(1L).build(),
                                AvailabilityItemRequest.builder().productId(7L).build()))
                        .build());

        assertThat(response.getItems().get(0).getRequested()).isEqualTo(1);
        assertThat(response.getItems().get(0).isSufficient()).isTrue();
        assertThat(response.getItems().get(1).getAvailable()).isZero();
        assertThat(response.getItems().get(1).isInStock()).isFalse();
        assertThat(response.isAllAvailable()).isFalse();
    }

    // ------------------------------------------------------------------ expiry

    @Test
    @DisplayName("expiry: returns overdue reservations to stock and marks them EXPIRED")
    void expiry_releasesOverdueReservations() {

        given(reservationRepository.findByStatusAndExpiresAtBefore(
                eq(ReservationStatus.RESERVED), any(LocalDateTime.class)))
                .willReturn(List.of(
                        reservation(500L, 1L, 2, ReservationStatus.RESERVED),
                        reservation(501L, 2L, 1, ReservationStatus.RESERVED)));
        given(inventoryRepository.release(anyLong(), anyInt(), any())).willReturn(1);

        int expired = reservationService.expireOverdueReservations();

        assertThat(expired).isEqualTo(2);
        then(stockMovementRecorder).should()
                .record(1L, 2, StockMovementReason.EXPIRE, "order:500");
        then(stockMovementRecorder).should()
                .record(2L, 1, StockMovementReason.EXPIRE, "order:501");
    }

    @Test
    @DisplayName("expiry: nothing overdue means no work")
    void expiry_doesNothingWhenNothingIsOverdue() {

        given(reservationRepository.findByStatusAndExpiresAtBefore(
                eq(ReservationStatus.RESERVED), any(LocalDateTime.class)))
                .willReturn(List.of());

        assertThat(reservationService.expireOverdueReservations()).isZero();
        then(inventoryRepository).should(never()).release(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("expiry: a reservation whose stock no longer matches stays RESERVED for a retry")
    void expiry_leavesUnreleasableReservationReserved() {

        InventoryReservation stuck = reservation(500L, 1L, 2, ReservationStatus.RESERVED);
        given(reservationRepository.findByStatusAndExpiresAtBefore(
                eq(ReservationStatus.RESERVED), any(LocalDateTime.class)))
                .willReturn(List.of(stuck));
        given(inventoryRepository.release(anyLong(), anyInt(), any())).willReturn(0);

        assertThat(reservationService.expireOverdueReservations()).isZero();
        assertThat(stuck.getStatus()).isEqualTo(ReservationStatus.RESERVED);
    }
}
