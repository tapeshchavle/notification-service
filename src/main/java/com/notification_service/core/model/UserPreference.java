package com.notification_service.core.model;

import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Represents a user's notification preferences.
 * <p>
 * Controls which channels are enabled and quiet hours during which
 * non-critical notifications are suppressed.
 * <p>
 * In production, this would be persisted to a database.
 * Currently backed by an in-memory store for demonstration.
 */
public class UserPreference {

    private final String userId;
    private Set<NotificationType> enabledChannels;
    private LocalTime quietHoursStart;
    private LocalTime quietHoursEnd;

    public UserPreference(String userId) {
        this.userId = userId;
        // Default: all channels enabled, no quiet hours
        this.enabledChannels = EnumSet.allOf(NotificationType.class);
        this.quietHoursStart = null;
        this.quietHoursEnd = null;
    }

    public UserPreference(String userId, Set<NotificationType> enabledChannels,
                          LocalTime quietHoursStart, LocalTime quietHoursEnd) {
        this.userId = userId;
        this.enabledChannels = enabledChannels != null
                ? EnumSet.copyOf(enabledChannels)
                : EnumSet.allOf(NotificationType.class);
        this.quietHoursStart = quietHoursStart;
        this.quietHoursEnd = quietHoursEnd;
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
            // e.g., 22:00 - 07:00 does NOT apply here; this is 08:00 - 17:00 style
            return !now.isBefore(quietHoursStart) && now.isBefore(quietHoursEnd);
        } else {
            // Overnight range: e.g., 22:00 - 07:00
            return !now.isBefore(quietHoursStart) || now.isBefore(quietHoursEnd);
        }
    }

    // ── Getters & Setters ──────────────────────────────────────────────

    public String getUserId() { return userId; }

    public Set<NotificationType> getEnabledChannels() {
        return EnumSet.copyOf(enabledChannels);
    }

    public void setEnabledChannels(Set<NotificationType> enabledChannels) {
        this.enabledChannels = EnumSet.copyOf(enabledChannels);
    }

    public LocalTime getQuietHoursStart() { return quietHoursStart; }
    public void setQuietHoursStart(LocalTime quietHoursStart) { this.quietHoursStart = quietHoursStart; }

    public LocalTime getQuietHoursEnd() { return quietHoursEnd; }
    public void setQuietHoursEnd(LocalTime quietHoursEnd) { this.quietHoursEnd = quietHoursEnd; }
}
