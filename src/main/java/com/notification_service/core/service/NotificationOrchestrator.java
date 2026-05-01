package com.notification_service.core.service;

import com.notification_service.core.model.Notification;
import com.notification_service.core.model.NotificationPriority;
import com.notification_service.core.model.NotificationType;
import com.notification_service.queue.NotificationQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Facade — the single entry point for all notification operations.
 * <p>
 * Responsibilities:
 * <ol>
 *   <li>Validates the notification request</li>
 *   <li>Checks user preferences (channel enabled? quiet hours?)</li>
 *   <li>Creates the {@link Notification} domain entity</li>
 *   <li>Enqueues it to the {@link NotificationQueue} for async processing</li>
 * </ol>
 * <p>
 * This corresponds to the "Notification Service" box in the architecture diagram.
 * External services (Service 1, Service 2) interact with this via the REST API,
 * and this orchestrator delegates to the queue for async fan-out to channel processors.
 *
 * @see com.notification_service.queue.NotificationQueueConsumer
 * @see com.notification_service.channel.ChannelProcessorRegistry
 */
@Service
public class NotificationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(NotificationOrchestrator.class);

    private final UserPreferenceService userPreferenceService;
    private final NotificationQueue notificationQueue;

    public NotificationOrchestrator(UserPreferenceService userPreferenceService,
                                     NotificationQueue notificationQueue) {
        this.userPreferenceService = userPreferenceService;
        this.notificationQueue = notificationQueue;
    }

    /**
     * Sends a notification immediately (enqueues for async processing).
     *
     * @param userId   the target user
     * @param type     the delivery channel
     * @param subject  notification subject/title
     * @param body     notification body
     * @param metadata channel-specific metadata (e.g., email address, phone number)
     * @param priority delivery priority
     * @return the created Notification with its generated ID
     * @throws IllegalArgumentException if the user has disabled this channel
     */
    public Notification sendNotification(String userId, NotificationType type, String subject,
                                          String body, java.util.Map<String, String> metadata,
                                          NotificationPriority priority) {

        log.info("Received notification request: userId={}, type={}, priority={}", userId, type, priority);

        // ── Step 1: Check user preferences ─────────────────────────────
        if (!userPreferenceService.isChannelEnabled(userId, type)) {
            String msg = String.format("Channel %s is disabled for user %s", type, userId);
            log.warn(msg);
            throw new IllegalArgumentException(msg);
        }

        // Check quiet hours (CRITICAL notifications bypass)
        if (priority != NotificationPriority.CRITICAL &&
            userPreferenceService.isInQuietHours(userId)) {
            log.info("User {} is in quiet hours — suppressing {} notification (non-critical)",
                    userId, type);
            // In production: could queue for later delivery instead of rejecting
            throw new IllegalArgumentException(
                    "User " + userId + " is in quiet hours. Only CRITICAL notifications are allowed.");
        }

        // ── Step 2: Build the notification entity ──────────────────────
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .subject(subject)
                .body(body)
                .metadata(metadata)
                .priority(priority)
                .build();

        log.info("Created notification: {} for userId: {} via {}", notification.getId(), userId, type);

        // ── Step 3: Enqueue for async processing ───────────────────────
        notificationQueue.enqueue(notification);
        log.info("Notification enqueued: {} | Queue size: {}", notification.getId(), notificationQueue.size());

        return notification;
    }

    /**
     * Sends a notification with default MEDIUM priority.
     */
    public Notification sendNotification(String userId, NotificationType type,
                                          String subject, String body,
                                          java.util.Map<String, String> metadata) {
        return sendNotification(userId, type, subject, body, metadata, NotificationPriority.MEDIUM);
    }
}
