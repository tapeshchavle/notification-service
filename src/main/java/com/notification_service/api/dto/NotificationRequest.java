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
 */
public class NotificationRequest {

    @NotBlank(message = "userId is required")
    private String userId;

    @NotNull(message = "type is required (EMAIL, SMS, PUSH, IN_APP)")
    private NotificationType type;

    private String subject;

    @NotBlank(message = "body is required")
    private String body;

    /**
     * Channel-specific metadata.
     * <ul>
     *   <li>EMAIL: {@code {"email": "user@example.com"}}</li>
     *   <li>SMS: {@code {"phoneNumber": "+1234567890"}}</li>
     *   <li>PUSH: {@code {"deviceToken": "abc123", "platform": "ANDROID"}}</li>
     *   <li>IN_APP: no specific metadata required</li>
     * </ul>
     */
    private Map<String, String> metadata;

    private NotificationPriority priority = NotificationPriority.MEDIUM;

    // ── Constructors ───────────────────────────────────────────────────

    public NotificationRequest() {}

    public NotificationRequest(String userId, NotificationType type, String subject,
                                String body, Map<String, String> metadata,
                                NotificationPriority priority) {
        this.userId = userId;
        this.type = type;
        this.subject = subject;
        this.body = body;
        this.metadata = metadata;
        this.priority = priority != null ? priority : NotificationPriority.MEDIUM;
    }

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
}
