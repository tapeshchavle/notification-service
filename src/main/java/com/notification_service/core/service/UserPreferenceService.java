package com.notification_service.core.service;

import com.notification_service.core.model.NotificationType;
import com.notification_service.core.model.UserPreference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages user notification preferences.
 * <p>
 * Stores per-user configuration for:
 * <ul>
 *   <li>Which channels (EMAIL, SMS, PUSH, IN_APP) are enabled</li>
 *   <li>Quiet hours during which non-critical notifications are suppressed</li>
 * </ul>
 * <p>
 * Currently backed by an in-memory {@link ConcurrentHashMap}.
 * In production: replace with database-backed persistence (JPA/Mongo).
 * <p>
 * This corresponds to the "User Preference Service" box in the architecture diagram.
 */
@Service
public class UserPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(UserPreferenceService.class);

    private final Map<String, UserPreference> preferenceStore = new ConcurrentHashMap<>();

    /**
     * Retrieves preferences for a user.
     * Returns default preferences (all channels enabled) if none are configured.
     */
    public UserPreference getPreference(String userId) {
        return preferenceStore.computeIfAbsent(userId, id -> {
            log.debug("No preference found for userId: {} — returning defaults (all channels enabled)", id);
            return new UserPreference(id);
        });
    }

    /**
     * Checks if a specific channel is enabled for a user.
     */
    public boolean isChannelEnabled(String userId, NotificationType type) {
        UserPreference pref = getPreference(userId);
        boolean enabled = pref.isChannelEnabled(type);
        if (!enabled) {
            log.info("Channel {} is disabled for userId: {}", type, userId);
        }
        return enabled;
    }

    /**
     * Checks if the user is currently in quiet hours.
     * Critical notifications bypass quiet hours.
     */
    public boolean isInQuietHours(String userId) {
        UserPreference pref = getPreference(userId);
        return pref.isInQuietHours();
    }

    /**
     * Updates the preference for a user.
     */
    public void updatePreference(UserPreference preference) {
        preferenceStore.put(preference.getUserId(), preference);
        log.info("Updated preferences for userId: {} | Enabled channels: {}",
                preference.getUserId(), preference.getEnabledChannels());
    }

    /**
     * Retrieves the preference if it exists (without creating defaults).
     */
    public Optional<UserPreference> findPreference(String userId) {
        return Optional.ofNullable(preferenceStore.get(userId));
    }
}
