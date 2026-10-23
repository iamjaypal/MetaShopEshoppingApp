package com.shopMeta.inventory_service.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Releases reservations that were never confirmed (spec 9.4).
 *
 * <p>An order that reserves stock and then dies — the customer abandons checkout, order-service
 * crashes before confirming — would otherwise hold that stock forever. Every minute this sweeper asks
 * {@link ReservationService} to expire anything that has been RESERVED for longer than
 * {@code metashop.inventory.reservation-ttl}.
 *
 * <p>The work itself lives in the service so it runs in one transaction per sweep; this class only
 * owns the schedule.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpirySweeper {

    private final ReservationService reservationService;

    @Scheduled(fixedDelayString = "${metashop.inventory.sweep-interval}")
    public void sweep() {

        try {
            int expired = reservationService.expireOverdueReservations();
            if (expired > 0) {
                log.info("Sweeper released {} expired reservation(s)", expired);
            }
        } catch (Exception exception) {
            // Never let a failed sweep kill the scheduler thread.
            log.error("Reservation expiry sweep failed", exception);
        }
    }
}
