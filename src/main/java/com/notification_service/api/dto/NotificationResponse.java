package com.notification_service.api.dto;

import com.notification_service.core.model.NotificationStatus;
import com.notification_service.core.model.NotificationType;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for notification API responses.
 * Provides a clean, stable API contract independent of internal domain changes.
 * <p>
 * Use static factory methods for construction.
 */
public record NotificationResponse(
        String notificationId,
        NotificationStatus status,
        NotificationType type,
        String message,
        Instant timestamp,
        Map<String, Long> metrics
) {

    // ── Static Factory Methods ─────────────────────────────────────────

    public static NotificationResponse success(String notificationId, NotificationType type) {
        return new NotificationResponse(
                notificationId,
                NotificationStatus.QUEUED,
                type,
                "Notification accepted and queued for delivery via " + type,
                Instant.now(),
                null
        );
    }

    public static NotificationResponse scheduled(String scheduleId, NotificationType type, Instant scheduledAt) {
        return new NotificationResponse(
                scheduleId,
                NotificationStatus.PENDING,
                type,
                "Notification scheduled for delivery at " + scheduledAt + " via " + type,
                Instant.now(),
                null
        );
    }

    public static NotificationResponse fromNotification(String notificationId, NotificationStatus status,
                                                         NotificationType type) {
        return new NotificationResponse(
                notificationId,
                status,
                type,
                "Notification status: " + status,
                Instant.now(),
                null
        );
    }

    public static NotificationResponse error(String message) {
        return new NotificationResponse(
                null,
                null,
                null,
                message,
                Instant.now(),
                null
        );
    }

    public static NotificationResponse withMetrics(Map<String, Long> metrics) {
        return new NotificationResponse(
                null,
                null,
                null,
                "Current system metrics",
                Instant.now(),
                metrics
        );
    }
}
