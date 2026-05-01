package com.notification_service.core.model;

import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Represents a user's notification preferences (immutable).
 * <p>
 * Controls which channels are enabled and quiet hours during which
 * non-critical notifications are suppressed.
 * <p>
 * To update preferences, create a new instance with the desired values.
 * In production, this would be persisted to a database.
 */
public record UserPreference(
        String userId,
        Set<NotificationType> enabledChannels,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd
) {

    /**
     * Compact constructor — defensive copy of enabledChannels.
     */
    public UserPreference {
        enabledChannels = enabledChannels != null
                ? EnumSet.copyOf(enabledChannels)
                : EnumSet.allOf(NotificationType.class);
    }

    /**
     * Convenience constructor — creates default preferences (all channels enabled, no quiet hours).
     */
    public UserPreference(String userId) {
        this(userId, EnumSet.allOf(NotificationType.class), null, null);
    }

    /**
     * Checks if a specific notification channel is enabled for this user.
     */
    public boolean isChannelEnabled(NotificationType type) {
        return enabledChannels.contains(type);
    }

    /**
     * Checks if the current time falls within the user's quiet hours.
     * Critical notifications bypass quiet hours.
     */
    public boolean isInQuietHours() {
        if (quietHoursStart == null || quietHoursEnd == null) {
            return false;
        }
        LocalTime now = LocalTime.now();
        if (quietHoursStart.isBefore(quietHoursEnd)) {
            // Same-day range: e.g., 08:00 - 17:00
            return !now.isBefore(quietHoursStart) && now.isBefore(quietHoursEnd);
        } else {
            // Overnight range: e.g., 22:00 - 07:00
            return !now.isBefore(quietHoursStart) || now.isBefore(quietHoursEnd);
        }
    }
}
