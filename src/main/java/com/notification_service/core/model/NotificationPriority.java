package com.notification_service.core.model;

/**
 * Priority levels for notifications.
 * Higher priority notifications are dequeued first from the priority queue.
 */
public enum NotificationPriority {

    LOW(0),
    MEDIUM(1),
    HIGH(2),
    CRITICAL(3);

    private final int weight;

    NotificationPriority(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }
}
