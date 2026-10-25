package com.shopMeta.notification_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.shopMeta.notification_service.config.NotificationProperties;
import com.shopMeta.notification_service.dto.NotificationDetailResponse;
import com.shopMeta.notification_service.dto.NotificationPageResponse;
import com.shopMeta.notification_service.dto.NotificationResponse;
import com.shopMeta.notification_service.entity.Notification;
import com.shopMeta.notification_service.entity.NotificationStatus;
import com.shopMeta.notification_service.entity.NotificationType;
import com.shopMeta.notification_service.event.EventEnvelope;
import com.shopMeta.notification_service.exception.BusinessRuleException;
import com.shopMeta.notification_service.exception.NotificationDeliveryException;
import com.shopMeta.notification_service.exception.ResourceNotFoundException;
import com.shopMeta.notification_service.mail.MailNotificationSender;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The orchestration around a notification: skip what should be skipped, mark what happened, and never
 * hold a transaction across the SMTP call.
 */
class NotificationServiceTest {

    private static final String EVENT_ID = "evt-1";

    private static final String EMAIL = "asha@example.com";

    private final NotificationStore store = mock(NotificationStore.class);

    private final NotificationPlanner planner = mock(NotificationPlanner.class);

    private final MailNotificationSender sender = mock(MailNotificationSender.class);

    private final NotificationProperties properties = new NotificationProperties();

    private final NotificationService service =
            new NotificationService(store, planner, sender, properties);

    // ------------------------------------------------------------- consume

    @Test
    void handle_sendsTheEmailAndRecordsTheEvent() {

        NotificationPlan plan = plan();
        given(planner.plan(any())).willReturn(plan);
        given(store.preparePending(plan)).willReturn(5L);

        service.handle(envelope());

        then(sender).should().send(EMAIL, "Order MS-000042 confirmed", "<p>Hi</p>");
        then(store).should().markSentFromEvent(5L, EVENT_ID, EventEnvelope.Types.ORDER_CONFIRMED);
        then(store).should(never()).markFailed(anyLong(), anyString());
    }

    @Test
    void handle_skipsAnEventThatHasAlreadyBeenHandled() {

        given(store.isProcessed(EVENT_ID)).willReturn(true);

        service.handle(envelope());

        then(planner).shouldHaveNoInteractions();
        then(sender).shouldHaveNoInteractions();
    }

    @Test
    void handle_skipsAnEventTypeThatProducesNoNotification() {

        given(planner.plan(any())).willReturn(null);

        service.handle(envelope());

        then(store).should(never()).preparePending(any());
        then(sender).shouldHaveNoInteractions();
    }

    @Test
    void handle_skipsAnEnvelopeWithoutAnIdOrType() {

        service.handle(new EventEnvelope(null, null, null, null, null, null));

        then(store).shouldHaveNoInteractions();
        then(planner).shouldHaveNoInteractions();
    }

    @Test
    void handle_marksFailedAndRethrowsSoTheRecordIsRetriedAndDeadLettered() {

        NotificationPlan plan = plan();
        given(planner.plan(any())).willReturn(plan);
        given(store.preparePending(plan)).willReturn(5L);
        willThrow(new NotificationDeliveryException("smtp down"))
                .given(sender).send(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.handle(envelope()))
                .isInstanceOf(NotificationDeliveryException.class)
                .hasMessageContaining("smtp down");

        then(store).should().markFailed(5L, "smtp down");
        then(store).should(never()).markSentFromEvent(anyLong(), anyString(), anyString());
    }

    /**
     * The row is written before the send, so a crash half way through leaves evidence rather than an
     * email that vanished.
     */
    @Test
    void handle_preparesTheRowBeforeCallingTheMailServer() {

        NotificationPlan plan = plan();
        given(planner.plan(any())).willReturn(plan);
        given(store.preparePending(plan)).willReturn(5L);

        service.handle(envelope());

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(store, sender);
        order.verify(store).preparePending(plan);
        order.verify(sender).send(EMAIL, "Order MS-000042 confirmed", "<p>Hi</p>");
        order.verify(store).markSentFromEvent(5L, EVENT_ID, EventEnvelope.Types.ORDER_CONFIRMED);
    }

    // --------------------------------------------------------------- admin

    @Test
    void list_returnsAPageNewestFirstWithNoStatusFilter() {

        given(store.findPage(org.mockito.ArgumentMatchers.isNull(), any(Pageable.class)))
                .willReturn(pageOf(notification(NotificationStatus.SENT)));

        NotificationPageResponse response = service.list(null, 0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.isLast()).isTrue();
    }

    @Test
    void list_passesTheStatusFilterThrough() {

        given(store.findPage(any(), any(Pageable.class)))
                .willReturn(pageOf(notification(NotificationStatus.FAILED)));

        NotificationPageResponse response = service.list(NotificationStatus.FAILED, 0, 20);

        assertThat(response.getContent().get(0).getStatus()).isEqualTo(NotificationStatus.FAILED);
        then(store).should().findPage(
                org.mockito.ArgumentMatchers.eq(NotificationStatus.FAILED), any(Pageable.class));
    }

    @Test
    void list_clampsAnOversizedPageRequestToTheConfiguredMaximum() {

        properties.setMaxPageSize(50);
        given(store.findPage(any(), any(Pageable.class))).willReturn(pageOf());

        service.list(null, -3, 5000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        then(store).should().findPage(org.mockito.ArgumentMatchers.isNull(), captor.capture());

        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        assertThat(captor.getValue().getPageNumber()).isZero();
    }

    @Test
    void detail_includesTheRenderedBody() {

        Notification notification = notification(NotificationStatus.SENT);
        given(store.get(5L)).willReturn(notification);

        NotificationDetailResponse response = service.detail(5L);

        assertThat(response.getNotification().getId()).isEqualTo(5L);
        assertThat(response.getBody()).isEqualTo("<p>Hi</p>");
    }

    @Test
    void retry_reSendsAFailedNotification() {

        Notification notification = notification(NotificationStatus.FAILED);
        given(store.get(5L)).willReturn(notification);

        NotificationResponse response = service.retry(5L);

        then(sender).should().send(EMAIL, "Order MS-000042 confirmed", "<p>Hi</p>");
        then(store).should().markPendingForRetry(5L);
        then(store).should().markSent(5L);
        assertThat(response.getId()).isEqualTo(5L);
    }

    @Test
    void retry_refusesAnAlreadySentNotification() {

        given(store.get(5L)).willReturn(notification(NotificationStatus.SENT));

        assertThatThrownBy(() -> service.retry(5L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already sent");

        then(sender).shouldHaveNoInteractions();
    }

    @Test
    void retry_recordsTheFailureAndRethrowsWhenTheMailServerRefusesAgain() {

        given(store.get(5L)).willReturn(notification(NotificationStatus.FAILED));
        willThrow(new NotificationDeliveryException("smtp down"))
                .given(sender).send(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.retry(5L))
                .isInstanceOf(NotificationDeliveryException.class);

        then(store).should().markFailed(5L, "smtp down");
        then(store).should(never()).markSent(anyLong());
    }

    @Test
    void retry_propagatesAnUnknownId() {

        given(store.get(99L)).willThrow(new ResourceNotFoundException("Notification 99 was not found"));

        assertThatThrownBy(() -> service.retry(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void retry_canBeCalledAgainAfterAFailedAttempt() {

        given(store.get(5L)).willReturn(notification(NotificationStatus.FAILED));

        service.retry(5L);
        service.retry(5L);

        then(sender).should(times(2)).send(anyString(), anyString(), anyString());
    }

    // ------------------------------------------------------------- helpers

    private static EventEnvelope envelope() {
        return new EventEnvelope(EVENT_ID, EventEnvelope.Types.ORDER_CONFIRMED,
                java.time.OffsetDateTime.now(), 1, null, null);
    }

    private static NotificationPlan plan() {
        return new NotificationPlan(EVENT_ID, 7L, EMAIL, NotificationType.ORDER_CONFIRMED,
                "Order MS-000042 confirmed", "<p>Hi</p>");
    }

    private static Notification notification(NotificationStatus status) {
        return Notification.builder()
                .id(5L)
                .eventId(EVENT_ID)
                .userId(7L)
                .email(EMAIL)
                .type(NotificationType.ORDER_CONFIRMED)
                .subject("Order MS-000042 confirmed")
                .body("<p>Hi</p>")
                .status(status)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static Page<Notification> pageOf(Notification... notifications) {
        List<Notification> content = List.of(notifications);
        return new PageImpl<>(content, PageRequest.of(0, 20), content.size());
    }
}
