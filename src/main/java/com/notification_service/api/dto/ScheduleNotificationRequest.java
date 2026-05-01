package com.notification_service.api.dto;

import com.notification_service.core.model.NotificationPriority;
import com.notification_service.core.model.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for scheduling a notification for future delivery.
 * Maps to: {@code POST /api/v1/notifications/schedule}
 */
public class ScheduleNotificationRequest {

    @NotBlank(message = "userId is required")
    private String userId;

    @NotNull(message = "type is required (EMAIL, SMS, PUSH, IN_APP)")
    private NotificationType type;

    private String subject;

    @NotBlank(message = "body is required")
    private String body;

    private Map<String, String> metadata;

    private NotificationPriority priority = NotificationPriority.MEDIUM;

    @NotNull(message = "scheduledAt is required (ISO-8601 timestamp)")
    private Instant scheduledAt;

    // ── Constructors ───────────────────────────────────────────────────

    public ScheduleNotificationRequest() {}

    // ── Getters & Setters ──────────────────────────────────────────────

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    public NotificationPriority getPriority() { return priority; }
    public void setPriority(NotificationPriority priority) { this.priority = priority; }

    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
}
