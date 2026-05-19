package com.syuuk.patentflow.notification.dto;

import java.time.OffsetDateTime;

public record NotificationResponse(
        String notificationId,
        String title,
        String message,
        String targetRole,
        boolean isRead,
        OffsetDateTime createdAt,
        String link
) {
}
