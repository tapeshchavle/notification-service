package com.notification_service.core.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Core domain entity representing a notification in the system.
 * <p>
 * This is the central data structure that flows through the entire pipeline:
 * API → Orchestrator → Queue → Consumer → ChannelProcessor
 * <p>
 * The entity is mutable for status transitions and retry tracking,
 * but core fields (id, userId, type, subject, body) are set at construction.
 */
public class Notification implements Comparable<Notification> {

    private final String id;
    private final String userId;
    private final NotificationType type;
    private final String subject;
    private final String body;
    private final Map<String, String> metadata;
    private final NotificationPriority priority;
    private final Instant createdAt;

    private NotificationStatus status;
    private int retryCount;
    private int maxRetries;
    private Instant nextRetryAt;
    private Instant updatedAt;
    private String failureReason;

    // ── Constructor (Builder-style via static factory) ─────────────────

    private Notification(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.userId = builder.userId;
        this.type = builder.type;
        this.subject = builder.subject;
        this.body = builder.body;
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
        this.priority = builder.priority != null ? builder.priority : NotificationPriority.MEDIUM;
        this.status = NotificationStatus.PENDING;
        this.retryCount = 0;
        this.maxRetries = builder.maxRetries > 0 ? builder.maxRetries : 3;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    // ── Priority-based ordering for PriorityBlockingQueue ──────────────

    @Override
    public int compareTo(Notification other) {
        // Higher priority weight = dequeued first (reverse natural order)
        return Integer.compare(other.priority.getWeight(), this.priority.getWeight());
    }

    // ── Status Transition Methods ──────────────────────────────────────

    public void markQueued() {
        this.status = NotificationStatus.QUEUED;
        this.updatedAt = Instant.now();
    }

    public void markProcessing() {
        this.status = NotificationStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = NotificationStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void markRetryScheduled(Instant nextRetry) {
        this.status = NotificationStatus.RETRY_SCHEDULED;
        this.nextRetryAt = nextRetry;
        this.retryCount++;
        this.updatedAt = Instant.now();
    }

    public void markDeadLettered() {
        this.status = NotificationStatus.DEAD_LETTERED;
        this.updatedAt = Instant.now();
    }

    public void markCancelled() {
        this.status = NotificationStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    // ── Getters ────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public Map<String, String> getMetadata() { return metadata; }
    public NotificationPriority getPriority() { return priority; }
    public NotificationStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public int getMaxRetries() { return maxRetries; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getFailureReason() { return failureReason; }

    @Override
    public String toString() {
        return String.format("Notification{id='%s', type=%s, userId='%s', status=%s, priority=%s, retries=%d/%d}",
                id, type, userId, status, priority, retryCount, maxRetries);
    }

    // ── Builder ────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String userId;
        private NotificationType type;
        private String subject;
        private String body;
        private Map<String, String> metadata;
        private NotificationPriority priority;
        private int maxRetries;

        public Builder id(String id) { this.id = id; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder type(NotificationType type) { this.type = type; return this; }
        public Builder subject(String subject) { this.subject = subject; return this; }
        public Builder body(String body) { this.body = body; return this; }
        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }
        public Builder priority(NotificationPriority priority) { this.priority = priority; return this; }
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }

        public Notification build() {
            if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is required");
            if (type == null) throw new IllegalArgumentException("type is required");
            if (body == null || body.isBlank()) throw new IllegalArgumentException("body is required");
            return new Notification(this);
        }
    }
}
