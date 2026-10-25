package com.shopMeta.notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * One page of the admin notification log (spec 5.3 pagination convention).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPageResponse {

    private List<NotificationResponse> content;

    private int page;

    private int size;

    private long totalElements;

    private int totalPages;

    private boolean last;
}
