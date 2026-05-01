package com.notification_service.api.dto;

import com.notification_service.core.model.NotificationPriority;
import com.notification_service.core.model.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * DTO for incoming notification requests from external services.
 * <p>
 * Validated using Jakarta Bean Validation annotations.
 * Maps to the REST API endpoint: {@code POST /api/v1/notifications}
 * <p>
 * Channel-specific {@code metadata}:
 * <ul>
 *   <li>EMAIL: {@code {"email": "user@example.com"}}</li>
 *   <li>SMS: {@code {"phoneNumber": "+1234567890"}}</li>
 *   <li>PUSH: {@code {"deviceToken": "abc123", "platform": "ANDROID"}}</li>
 *   <li>IN_APP: no specific metadata required</li>
 * </ul>
 */
public record NotificationRequest(

        @NotBlank(message = "userId is required")
        String userId,

        @NotNull(message = "type is required (EMAIL, SMS, PUSH, IN_APP)")
        NotificationType type,

        String subject,

        @NotBlank(message = "body is required")
        String body,

        Map<String, String> metadata,

        NotificationPriority priority
) {
    /**
     * Compact constructor — defaults priority to MEDIUM if not provided.
     */
    public NotificationRequest {
        if (priority == null) {
            priority = NotificationPriority.MEDIUM;
        }
    }
}
