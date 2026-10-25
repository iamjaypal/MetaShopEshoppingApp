package com.shopMeta.notification_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopMeta.notification_service.entity.ProcessedEvent;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
