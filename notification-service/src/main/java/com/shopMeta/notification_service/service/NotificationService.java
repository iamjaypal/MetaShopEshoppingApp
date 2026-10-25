package com.shopMeta.notification_service.service;

import com.shopMeta.notification_service.config.NotificationProperties;
import com.shopMeta.notification_service.dto.NotificationDetailResponse;
import com.shopMeta.notification_service.dto.NotificationPageResponse;
import com.shopMeta.notification_service.dto.NotificationResponse;
import com.shopMeta.notification_service.entity.Notification;
import com.shopMeta.notification_service.entity.NotificationStatus;
import com.shopMeta.notification_service.event.EventEnvelope;
import com.shopMeta.notification_service.exception.BusinessRuleException;
import com.shopMeta.notification_service.exception.NotificationDeliveryException;
import com.shopMeta.notification_service.mail.MailNotificationSender;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * The two halves of this service: consuming events, and letting an admin look at and retry what was
 * sent (spec 13).
 *
 * <p>This class deliberately holds no transaction. Persistence is delegated to
 * {@link NotificationStore}, so the SMTP call happens between two short transactions and never inside
 * one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationStore store;

    private final NotificationPlanner planner;

    private final MailNotificationSender sender;

    private final NotificationProperties properties;

    // --------------------------------------------------------------- consumer

    /**
     * Handles one event from Kafka.
     *
     * <p>Three things are cheap to skip and are skipped: an envelope without an id or type, an event
     * that has already been handled, and an event type that maps to no email.
     *
     * <p>A delivery failure is <em>not</em> swallowed. The notification row is marked {@code FAILED}
     * first, then the exception is rethrown so Spring Kafka's error handler retries the record with
     * exponential backoff and finally publishes it to {@code <topic>.DLT} (spec 13 / 14.4).
     */
    public void handle(EventEnvelope envelope) {

        if (envelope == null || !hasText(envelope.eventId()) || !hasText(envelope.eventType())) {
            log.warn("Skipping an event with no id or type: {}", envelope);
            return;
        }

        if (store.isProcessed(envelope.eventId())) {
            log.debug("Event {} ({}) was already handled; skipping",
                    envelope.eventId(), envelope.eventType());
            return;
        }

        NotificationPlan plan = planner.plan(envelope);

        if (plan == null) {
            log.debug("Event {} ({}) produces no notification; skipping",
                    envelope.eventId(), envelope.eventType());
            return;
        }

        deliver(plan, envelope.eventType());
    }

    private void deliver(NotificationPlan plan, String eventType) {

        // The row exists before the send, so a crash mid-send leaves a PENDING row to investigate
        // rather than an email that vanished without a trace.
        Long id = store.preparePending(plan);

        try {
            sender.send(plan.email(), plan.subject(), plan.body());

        } catch (RuntimeException ex) {
            store.markFailed(id, ex.getMessage());
            log.warn("Delivery of {} ({}) to {} failed: {}",
                    plan.type(), plan.eventId(), plan.email(), ex.getMessage());

            throw ex instanceof NotificationDeliveryException delivery
                    ? delivery
                    : new NotificationDeliveryException(ex.getMessage(), ex);
        }

        store.markSentFromEvent(id, plan.eventId(), eventType);
        log.info("Sent {} ({}) to {}", plan.type(), plan.eventId(), plan.email());
    }

    // ------------------------------------------------------------------ admin

    /**
     * One page of the notification log, newest first (spec 13).
     *
     * @param status optional filter; {@code null} means every status
     */
    public NotificationPageResponse list(NotificationStatus status, int page, int size) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), properties.getMaxPageSize()));

        Page<Notification> result = store.findPage(status, pageable);

        List<NotificationResponse> content = result.getContent().stream()
                .map(NotificationService::toResponse)
                .toList();

        return NotificationPageResponse.builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    /** One notification including the rendered HTML that was (or will be) sent. */
    public NotificationDetailResponse detail(Long id) {

        Notification notification = store.get(id);

        return NotificationDetailResponse.builder()
                .notification(toResponse(notification))
                .body(notification.getBody())
                .build();
    }

    /**
     * Re-sends a notification from the admin API (spec 13).
     *
     * <p>Unlike the consumer path this does not go through Kafka and is not retried automatically: the
     * admin asked once and gets one answer. A failure still updates the row to {@code FAILED} with the
     * reason, so the button can simply be pressed again.
     *
     * @throws BusinessRuleException if the notification was already delivered
     */
    public NotificationResponse retry(Long id) {

        Notification notification = store.get(id);

        if (notification.getStatus() == NotificationStatus.SENT) {
            throw new BusinessRuleException(
                    "Notification " + id + " was already sent and cannot be retried");
        }

        store.markPendingForRetry(id);

        try {
            sender.send(notification.getEmail(), notification.getSubject(), notification.getBody());

        } catch (RuntimeException ex) {
            store.markFailed(id, ex.getMessage());
            log.warn("Manual retry of notification {} to {} failed: {}",
                    id, notification.getEmail(), ex.getMessage());
            throw ex;
        }

        store.markSent(id);
        log.info("Manually retried notification {} to {}", id, notification.getEmail());

        return toResponse(store.get(id));
    }

    static NotificationResponse toResponse(Notification notification) {

        return NotificationResponse.builder()
                .id(notification.getId())
                .eventId(notification.getEventId())
                .userId(notification.getUserId())
                .email(notification.getEmail())
                .type(notification.getType())
                .subject(notification.getSubject())
                .status(notification.getStatus())
                .retryCount(notification.getRetryCount() == null ? 0 : notification.getRetryCount())
                .error(notification.getError())
                .createdAt(notification.getCreatedAt())
                .sentAt(notification.getSentAt())
                .build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
