package com.shopMeta.inventory_service.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.shopMeta.inventory_service.dto.ReservationItemRequest;
import com.shopMeta.inventory_service.dto.ReserveRequest;
import com.shopMeta.inventory_service.entity.Inventory;
import com.shopMeta.inventory_service.entity.InventoryReservation;
import com.shopMeta.inventory_service.entity.ReservationStatus;
import com.shopMeta.inventory_service.exception.InsufficientStockException;
import com.shopMeta.inventory_service.repository.InventoryRepository;
import com.shopMeta.inventory_service.repository.InventoryReservationRepository;
import com.shopMeta.inventory_service.service.ReservationService;

/**
 * The overselling proof (spec 9.4 / 17 phase 4).
 *
 * <p>These tests need a real database and real transactions — the guarantee under test is that the
 * database serialises concurrent conditional updates, which no mock can reproduce. The test class is
 * deliberately <em>not</em> {@code @Transactional}: each worker must run in its own transaction,
 * otherwise they would all share one connection and prove nothing.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        // Keep the expiry sweeper out of the way; these reservations must not be released mid-test.
        "metashop.inventory.sweep-interval=1h"
})
class ReservationConcurrencyTest {

    private static final int THREADS = 50;
    private static final long LAST_UNIT_PRODUCT = 900_001L;
    private static final long LIMITED_STOCK_PRODUCT = 900_002L;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @AfterEach
    void cleanUp() {
        for (long productId : List.of(LAST_UNIT_PRODUCT, LIMITED_STOCK_PRODUCT)) {
            reservationRepository.deleteAll(reservationRepository.findAll().stream()
                    .filter(reservation -> productId == reservation.getProductId())
                    .toList());
            inventoryRepository.findByProductId(productId).ifPresent(inventoryRepository::delete);
        }
    }

    private void givenStock(long productId, int available) {
        inventoryRepository.save(Inventory.builder()
                .productId(productId)
                .sku("SKU-" + productId)
                .availableQuantity(available)
                .reservedQuantity(0)
                .lowStockThreshold(1)
                .build());
    }

    private record Outcome(int succeeded, int rejected, List<Throwable> unexpected) {
    }

    /** Fires {@code THREADS} reservations for one unit at the same instant and counts the outcomes. */
    private Outcome race(long productId) throws InterruptedException {

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(THREADS);

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < THREADS; i++) {
                long orderId = 900_000L + i;

                pool.submit(() -> {
                    try {
                        startGate.await(); // all threads hit the service together

                        reservationService.reserve(ReserveRequest.builder()
                                .orderId(orderId)
                                .items(List.of(ReservationItemRequest.builder()
                                        .productId(productId)
                                        .quantity(1)
                                        .build()))
                                .build());

                        succeeded.incrementAndGet();

                    } catch (InsufficientStockException expected) {
                        rejected.incrementAndGet();
                    } catch (Throwable throwable) {
                        unexpected.add(throwable);
                    } finally {
                        doneGate.countDown();
                    }
                });
            }

            startGate.countDown();

            assertThat(doneGate.await(90, TimeUnit.SECONDS))
                    .as("all %d reservation attempts finished", THREADS)
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }

        return new Outcome(succeeded.get(), rejected.get(), unexpected);
    }

    private long reservedCount(long productId) {
        return reservationRepository.findAll().stream()
                .filter(reservation -> productId == reservation.getProductId())
                .filter(reservation -> reservation.getStatus() == ReservationStatus.RESERVED)
                .count();
    }

    @Test
    @DisplayName("50 threads race for the last unit: exactly one wins, nothing is oversold")
    void fiftyThreadsCannotOversellTheLastUnit() throws InterruptedException {

        givenStock(LAST_UNIT_PRODUCT, 1);

        Outcome outcome = race(LAST_UNIT_PRODUCT);

        assertThat(outcome.unexpected()).as("no unexpected failures").isEmpty();

        // THE assertion of spec 17 phase 4.
        assertThat(outcome.succeeded()).as("orders that got the unit").isEqualTo(1);
        assertThat(outcome.rejected()).as("orders correctly told it was gone").isEqualTo(THREADS - 1);

        Inventory stock = inventoryRepository.findByProductId(LAST_UNIT_PRODUCT).orElseThrow();
        assertThat(stock.getAvailableQuantity()).isZero();
        assertThat(stock.getReservedQuantity()).isEqualTo(1);

        assertThat(reservedCount(LAST_UNIT_PRODUCT)).isEqualTo(1);
    }

    @Test
    @DisplayName("20 units, 50 buyers: exactly 20 win and the counters stay consistent")
    void stockIsNeverOversoldWhenDemandExceedsSupply() throws InterruptedException {

        givenStock(LIMITED_STOCK_PRODUCT, 20);

        Outcome outcome = race(LIMITED_STOCK_PRODUCT);

        assertThat(outcome.unexpected()).as("no unexpected failures").isEmpty();
        assertThat(outcome.succeeded()).isEqualTo(20);
        assertThat(outcome.rejected()).isEqualTo(30);

        Inventory stock = inventoryRepository.findByProductId(LIMITED_STOCK_PRODUCT).orElseThrow();
        assertThat(stock.getAvailableQuantity()).isZero();
        assertThat(stock.getReservedQuantity()).isEqualTo(20);

        // available + reserved never drifts: 20 units in, 20 units held.
        assertThat(reservedCount(LIMITED_STOCK_PRODUCT)).isEqualTo(20);
    }

    @Test
    @DisplayName("a reservation released under contention returns the unit to available stock")
    void releasingUnderContentionRestoresStock() throws InterruptedException {

        givenStock(LAST_UNIT_PRODUCT, 1);

        // Winner takes the unit.
        reservationService.reserve(ReserveRequest.builder()
                .orderId(910_000L)
                .items(List.of(ReservationItemRequest.builder()
                        .productId(LAST_UNIT_PRODUCT).quantity(1).build()))
                .build());

        assertThat(inventoryRepository.findByProductId(LAST_UNIT_PRODUCT).orElseThrow()
                .getAvailableQuantity()).isZero();

        // Now 50 more orders race for a unit that only comes back once it is released.
        Outcome outcome = race(LAST_UNIT_PRODUCT);

        assertThat(outcome.succeeded()).as("the unit was still held, so none may win").isZero();
        assertThat(outcome.rejected()).isEqualTo(THREADS);

        reservationService.release(910_000L);

        Inventory released = inventoryRepository.findByProductId(LAST_UNIT_PRODUCT).orElseThrow();
        assertThat(released.getAvailableQuantity()).isEqualTo(1);
        assertThat(released.getReservedQuantity()).isZero();
    }
}
