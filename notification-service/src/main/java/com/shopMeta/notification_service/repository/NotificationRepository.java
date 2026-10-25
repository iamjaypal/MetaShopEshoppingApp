package com.shopMeta.notification_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.shopMeta.notification_service.entity.Notification;
import com.shopMeta.notification_service.entity.NotificationStatus;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Used by the consumer to reuse the row across redeliveries of the same event. */
    Optional<Notification> findByEventId(String eventId);

    Page<Notification> findByStatusOrderByCreatedAtDesc(NotificationStatus status, Pageable pageable);

    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(NotificationStatus status);
}
