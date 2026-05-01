package com.notification_service.api.dto;

import com.notification_service.core.model.NotificationStatus;
import com.notification_service.core.model.NotificationType;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for notification API responses.
 * Provides a clean, stable API contract independent of internal domain changes.
 */
public class NotificationResponse {

    private String notificationId;
    private NotificationStatus status;
    private NotificationType type;
    private String message;
    private Instant timestamp;
    private Map<String, Long> metrics;

    // ── Static Factory Methods ─────────────────────────────────────────

    public static NotificationResponse success(String notificationId, NotificationType type) {
        NotificationResponse response = new NotificationResponse();
        response.notificationId = notificationId;
        response.status = NotificationStatus.QUEUED;
        response.type = type;
        response.message = "Notification accepted and queued for delivery via " + type;
        response.timestamp = Instant.now();
        return response;
    }

    public static NotificationResponse scheduled(String scheduleId, NotificationType type, Instant scheduledAt) {
        NotificationResponse response = new NotificationResponse();
        response.notificationId = scheduleId;
        response.status = NotificationStatus.PENDING;
        response.type = type;
        response.message = "Notification scheduled for delivery at " + scheduledAt + " via " + type;
        response.timestamp = Instant.now();
        return response;
    }

    public static NotificationResponse fromNotification(String notificationId, NotificationStatus status,
                                                         NotificationType type) {
        NotificationResponse response = new NotificationResponse();
        response.notificationId = notificationId;
        response.status = status;
        response.type = type;
        response.message = "Notification status: " + status;
        response.timestamp = Instant.now();
        return response;
    }

    public static NotificationResponse error(String message) {
        NotificationResponse response = new NotificationResponse();
        response.status = null;
        response.message = message;
        response.timestamp = Instant.now();
        return response;
    }

    public static NotificationResponse withMetrics(Map<String, Long> metrics) {
        NotificationResponse response = new NotificationResponse();
        response.message = "Current system metrics";
        response.metrics = metrics;
        response.timestamp = Instant.now();
        return response;
    }

    // ── Getters & Setters ──────────────────────────────────────────────

    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }

    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Map<String, Long> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Long> metrics) { this.metrics = metrics; }
}
