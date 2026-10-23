package com.shopMeta.inventory_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shopMeta.inventory_service.entity.ProcessedEvent;

/**
 * Kafka consumer de-duplication (spec 9.2 / 14.4).
 *
 * <p>No consumer writes to it yet — the Kafka phase adds one. The repository exists so the table and
 * its entity are usable as soon as that consumer lands.
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
