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
public record ScheduleNotificationRequest(

        @NotBlank(message = "userId is required")
        String userId,

        @NotNull(message = "type is required (EMAIL, SMS, PUSH, IN_APP)")
        NotificationType type,

        String subject,

        @NotBlank(message = "body is required")
        String body,

        Map<String, String> metadata,

        NotificationPriority priority,

        @NotNull(message = "scheduledAt is required (ISO-8601 timestamp)")
        Instant scheduledAt
) {
    /**
     * Compact constructor — defaults priority to MEDIUM if not provided.
     */
    public ScheduleNotificationRequest {
        if (priority == null) {
            priority = NotificationPriority.MEDIUM;
        }
    }
}
