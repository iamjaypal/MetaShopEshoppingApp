package com.shopMeta.inventory_service.service;

import org.springframework.stereotype.Component;

import com.shopMeta.inventory_service.entity.StockMovement;
import com.shopMeta.inventory_service.entity.StockMovementReason;
import com.shopMeta.inventory_service.repository.StockMovementRepository;

import lombok.RequiredArgsConstructor;

/**
 * Writes the append-only stock audit trail (spec 9.2).
 *
 * <p>Every counter change — admin adjustment, reserve, release, confirm, expiry — goes through here,
 * so the history of a product's stock can always be reconstructed. Called inside the caller's
 * transaction: a movement is only recorded if the stock change itself commits.
 */
@Component
@RequiredArgsConstructor
public class StockMovementRecorder {

    private final StockMovementRepository stockMovementRepository;

    /**
     * @param changeQty signed delta applied to {@code available_quantity} (0 when the change only
     *                  affects reserved stock, as on confirm)
     * @param reference order id, actor email, ... — whatever explains the movement
     */
    public void record(Long productId, int changeQty, StockMovementReason reason, String reference) {

        stockMovementRepository.save(StockMovement.builder()
                .productId(productId)
                .changeQty(changeQty)
                .reason(reason)
                .reference(reference)
                .build());
    }
}
