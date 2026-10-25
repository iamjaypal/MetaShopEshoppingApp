package com.shopMeta.notification_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopMeta.notification_service.dto.NotificationDetailResponse;
import com.shopMeta.notification_service.dto.NotificationPageResponse;
import com.shopMeta.notification_service.dto.NotificationResponse;
import com.shopMeta.notification_service.entity.NotificationStatus;
import com.shopMeta.notification_service.entity.NotificationType;
import com.shopMeta.notification_service.exception.BusinessRuleException;
import com.shopMeta.notification_service.exception.NotificationDeliveryException;
import com.shopMeta.notification_service.exception.ResourceNotFoundException;
import com.shopMeta.notification_service.service.NotificationService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Web-layer tests: routing, query-parameter handling and the mapping of service exceptions to HTTP
 * statuses. The filter chain is off (D-038) — the ADMIN rule on {@code /api/v1/admin/**} is asserted
 * separately by {@link com.shopMeta.notification_service.config.SecurityConfigTest}.
 */
@WebMvcTest(AdminNotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void list_returnsThePageWithoutAnEnvelope() throws Exception {

        given(notificationService.list(any(), anyInt(), anyInt())).willReturn(pageOf());

        mockMvc.perform(get("/api/v1/admin/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(5))
                .andExpect(jsonPath("$.content[0].email").value("asha@example.com"))
                .andExpect(jsonPath("$.content[0].status").value("SENT"))
                .andExpect(jsonPath("$.content[0].type").value("ORDER_CONFIRMED"))
                .andExpect(jsonPath("$.content[0].body").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void list_passesTheStatusFilterAndPagingThrough() throws Exception {

        given(notificationService.list(eq(NotificationStatus.FAILED), eq(2), eq(5)))
                .willReturn(pageOf());

        mockMvc.perform(get("/api/v1/admin/notifications")
                        .param("status", "FAILED")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());
    }

    @Test
    void list_rejectsAnUnknownStatus() throws Exception {

        mockMvc.perform(get("/api/v1/admin/notifications").param("status", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid value for 'status'"));
    }

    @Test
    void get_returnsTheRenderedBody() throws Exception {

        given(notificationService.detail(5L)).willReturn(NotificationDetailResponse.builder()
                .notification(response())
                .body("<p>Hi Asha</p>")
                .build());

        mockMvc.perform(get("/api/v1/admin/notifications/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("<p>Hi Asha</p>"))
                .andExpect(jsonPath("$.notification.id").value(5));
    }

    @Test
    void get_returns404ForAnUnknownNotification() throws Exception {

        given(notificationService.detail(99L))
                .willThrow(new ResourceNotFoundException("Notification 99 was not found"));

        mockMvc.perform(get("/api/v1/admin/notifications/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Notification 99 was not found"));
    }

    @Test
    void retry_returnsTheUpdatedNotification() throws Exception {

        given(notificationService.retry(5L)).willReturn(response());

        mockMvc.perform(post("/api/v1/admin/notifications/5/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void retry_returns422ForAnAlreadySentNotification() throws Exception {

        given(notificationService.retry(5L))
                .willThrow(new BusinessRuleException(
                        "Notification 5 was already sent and cannot be retried"));

        mockMvc.perform(post("/api/v1/admin/notifications/5/retry"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "Notification 5 was already sent and cannot be retried"));
    }

    @Test
    void retry_returns503WhenTheMailServerRefusesAgain() throws Exception {

        willThrow(new NotificationDeliveryException("Mail server rejected the message for asha@example.com"))
                .given(notificationService).retry(5L);

        mockMvc.perform(post("/api/v1/admin/notifications/5/retry"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "Mail server rejected the message for asha@example.com"));
    }

    @Test
    void retry_returns404ForAnUnknownNotification() throws Exception {

        given(notificationService.retry(99L))
                .willThrow(new ResourceNotFoundException("Notification 99 was not found"));

        mockMvc.perform(post("/api/v1/admin/notifications/99/retry"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ------------------------------------------------------------- fixtures

    private static NotificationPageResponse pageOf() {
        return NotificationPageResponse.builder()
                .content(List.of(response()))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .last(true)
                .build();
    }

    private static NotificationResponse response() {
        return NotificationResponse.builder()
                .id(5L)
                .eventId("evt-1")
                .userId(7L)
                .email("asha@example.com")
                .type(NotificationType.ORDER_CONFIRMED)
                .subject("Order MS-000042 confirmed")
                .status(NotificationStatus.SENT)
                .retryCount(0)
                .createdAt(LocalDateTime.of(2026, 10, 22, 10, 15, 30))
                .sentAt(LocalDateTime.of(2026, 10, 22, 10, 15, 31))
                .build();
    }
}
