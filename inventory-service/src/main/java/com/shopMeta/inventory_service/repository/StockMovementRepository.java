package com.shopMeta.inventory_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shopMeta.inventory_service.entity.StockMovement;

/**
 * Append-only stock audit trail (spec 9.2).
 */
@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByProductIdOrderByCreatedAtDescIdDesc(Long productId);
}
