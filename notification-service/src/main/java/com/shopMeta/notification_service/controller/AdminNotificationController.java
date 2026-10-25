package com.shopMeta.notification_service.controller;

import com.shopMeta.notification_service.dto.NotificationDetailResponse;
import com.shopMeta.notification_service.dto.NotificationPageResponse;
import com.shopMeta.notification_service.dto.NotificationResponse;
import com.shopMeta.notification_service.entity.NotificationStatus;
import com.shopMeta.notification_service.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin view over the notification log (spec 13).
 *
 * <p>This is the service's entire HTTP surface. The ADMIN role is enforced by the security rule on
 * {@code /api/v1/admin/**}; nothing here is reachable without a JWT.
 */
@Tag(name = "Notifications (admin)", description = "The audit log of every email this service sent")
@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "List sent and failed notifications",
            description = "Paged, newest first, optionally filtered by status. The rendered body is "
                    + "not included — fetch a single notification for that.")
    @GetMapping
    public ResponseEntity<NotificationPageResponse> listNotifications(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        return ResponseEntity.ok(notificationService.list(status, page, size));
    }

    @Operation(summary = "Read one notification including the email that was sent",
            description = "The detail view is where an admin answers \"what did the customer "
                    + "actually receive?\"")
    @GetMapping("/{id}")
    public ResponseEntity<NotificationDetailResponse> getNotification(@PathVariable Long id) {

        return ResponseEntity.ok(notificationService.detail(id));
    }

    @Operation(summary = "Re-send a notification",
            description = "Replays the stored subject and body through SMTP once. Only a FAILED "
                    + "notification can be retried: a PENDING one is still being delivered and a SENT "
                    + "one would duplicate the email (422). If the mail server refuses again the "
                    + "notification stays FAILED with the new reason (503).")
    @PostMapping("/{id}/retry")
    public ResponseEntity<NotificationResponse> retryNotification(@PathVariable Long id) {

        return ResponseEntity.ok(notificationService.retry(id));
    }
}
