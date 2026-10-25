package com.shopMeta.notification_service.service;

import com.shopMeta.notification_service.entity.Notification;
import com.shopMeta.notification_service.entity.NotificationStatus;
import com.shopMeta.notification_service.entity.ProcessedEvent;
import com.shopMeta.notification_service.exception.ResourceNotFoundException;
import com.shopMeta.notification_service.repository.NotificationRepository;
import com.shopMeta.notification_service.repository.ProcessedEventRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

/**
 * Every write this service makes to its database, each in its own short transaction.
 *
 * <p>The split from {@link NotificationService} exists for the same reason as cart-service's
 * {@code CartStore} (D-062): the email is sent <em>between</em> two transactions, so no database
 * connection is held open while the SMTP round trip happens. It also means the {@code FAILED} state
 * survives the exception that the Kafka error handler then retries — if the failure were rolled back
 * along with the listener, the admin would have nothing to retry.
 */
@Service
@RequiredArgsConstructor
public class NotificationStore {

    private final NotificationRepository notificationRepository;

    private final ProcessedEventRepository processedEventRepository;

    /** Spec 14.4 de-duplication: has this event already been handled? */
    @Transactional(readOnly = true)
    public boolean isProcessed(String eventId) {
        return processedEventRepository.existsById(eventId);
    }

    /**
     * Returns the id of the {@code PENDING} row for this event, creating it on first sight and reusing
     * it on a redelivery so a retry updates one row instead of creating a second one.
     *
     * <p>The recipient, subject and rendered body are only written on creation: on a retry the stored
     * body is what gets re-sent, so an admin always resends exactly what was rendered the first time.
     *
     * @return the notification id
     */
    @Transactional
    public Long preparePending(NotificationPlan plan) {

        Notification notification = notificationRepository.findByEventId(plan.eventId())
                .orElseGet(() -> notificationRepository.save(Notification.builder()
                        .eventId(plan.eventId())
                        .userId(plan.userId())
                        .email(plan.email())
                        .type(plan.type())
                        .subject(plan.subject())
                        .body(plan.body())
                        .status(NotificationStatus.PENDING)
                        .retryCount(0)
                        .build()));

        if (notification.getStatus() != NotificationStatus.PENDING) {
            notification.markPendingForRetry();
            notification = notificationRepository.save(notification);
        }

        return notification.getId();
    }

    /**
     * Records a successful delivery of an event and marks that event as handled, in one transaction:
     * either the email is sent and the event is retired, or neither happened and the event will be
     * retried.
     */
    @Transactional
    public void markSentFromEvent(Long id, String eventId, String eventType) {

        markSent(id);

        if (eventId != null && !processedEventRepository.existsById(eventId)) {
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .processedAt(LocalDateTime.now())
                    .build());
        }
    }

    /** Records a successful delivery that did not come from a Kafka event (admin retry). */
    @Transactional
    public void markSent(Long id) {

        Notification notification = require(id);
        notification.markSent(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    /** Records a failed attempt, counting it and keeping the reason for the admin to read. */
    @Transactional
    public void markFailed(Long id, String reason) {

        Notification notification = require(id);
        notification.markFailed(reason);
        notificationRepository.save(notification);
    }

    /** Re-queues a notification for a manual retry, clearing the previous error. */
    @Transactional
    public void markPendingForRetry(Long id) {

        Notification notification = require(id);
        notification.markPendingForRetry();
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public Page<Notification> findPage(NotificationStatus status, Pageable pageable) {

        return status == null
                ? notificationRepository.findAllByOrderByCreatedAtDesc(pageable)
                : notificationRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Transactional(readOnly = true)
    public Notification get(Long id) {
        return require(id);
    }

    private Notification require(Long id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification " + id + " was not found"));
    }
}
