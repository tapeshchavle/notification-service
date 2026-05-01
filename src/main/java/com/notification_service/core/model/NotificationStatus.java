package com.notification_service.core.model;

/**
 * Represents the lifecycle status of a notification.
 * <p>
 * State transitions:
 * <pre>
 *   PENDING → QUEUED → PROCESSING → SENT
 *                  ↘              ↘
 *                CANCELLED    FAILED → RETRY_SCHEDULED → QUEUED
 *                                            ↘
 *                                      DEAD_LETTERED (max retries exhausted)
 * </pre>
 */
public enum NotificationStatus {

    /** Created but not yet enqueued */
    PENDING,

    /** Placed in the notification queue */
    QUEUED,

    /** Currently being processed by a channel processor */
    PROCESSING,

    /** Successfully delivered */
    SENT,

    /** Delivery failed */
    FAILED,

    /** Scheduled for retry with backoff */
    RETRY_SCHEDULED,

    /** Cancelled by user or system */
    CANCELLED,

    /** Max retries exhausted — moved to dead letter */
    DEAD_LETTERED
}
