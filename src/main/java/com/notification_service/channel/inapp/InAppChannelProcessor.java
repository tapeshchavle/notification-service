package com.notification_service.channel.inapp;

import com.notification_service.channel.AbstractChannelProcessor;
import com.notification_service.core.model.Notification;
import com.notification_service.core.model.NotificationType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-App notification channel processor.
 * <p>
 * Stores notifications in an in-memory store keyed by userId.
 * In production, this would:
 * - Persist to a database
 * - Push to the client via WebSocket or Server-Sent Events (SSE)
 * - Integrate with a real-time messaging service
 * <p>
 * Provides query methods for retrieving a user's in-app notifications.
 */
@Component
public class InAppChannelProcessor extends AbstractChannelProcessor {

    /**
     * In-memory store of in-app notifications per user.
     * In production: replace with database + WebSocket/SSE push.
     */
    private final Map<String, List<Notification>> userNotifications = new ConcurrentHashMap<>();

    public InAppChannelProcessor() {
        super(NotificationType.IN_APP);
    }

    @Override
    protected void doSend(Notification notification) {
        String userId = notification.getUserId();

        userNotifications
                .computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>())
                .add(notification);

        log.info("[IN_APP] Stored in-app notification for userId: {} | Subject: '{}' | Total stored: {}",
                userId, notification.getSubject(), userNotifications.get(userId).size());
    }

    /**
     * Retrieves all in-app notifications for a given user.
     *
     * @param userId the user's ID
     * @return list of in-app notifications (empty list if none)
     */
    public List<Notification> getNotificationsForUser(String userId) {
        return userNotifications.getOrDefault(userId, List.of());
    }

    /**
     * Returns the count of unread in-app notifications for a user.
     */
    public int getUnreadCount(String userId) {
        return userNotifications.getOrDefault(userId, List.of()).size();
    }

    /**
     * Clears all in-app notifications for a user (e.g., "mark all as read").
     */
    public void clearNotificationsForUser(String userId) {
        userNotifications.remove(userId);
        log.info("[IN_APP] Cleared all in-app notifications for userId: {}", userId);
    }
}
