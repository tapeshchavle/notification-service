package com.notification_service.retry;

import com.notification_service.core.model.Notification;

import java.time.Duration;

/**
 * Defines the retry policy contract for failed notifications.
 * <p>
 * Implementations determine whether a notification should be retried
 * and the delay before the next attempt.
 *
 * @see ExponentialBackoffRetryPolicy
 */
public interface RetryPolicy {

    /**
     * Determines whether the notification should be retried.
     *
     * @param notification the failed notification
     * @return true if another retry attempt should be made
     */
    boolean shouldRetry(Notification notification);

    /**
     * Calculates the delay before the next retry attempt.
     *
     * @param notification the failed notification (retryCount is used for backoff calculation)
     * @return the duration to wait before retrying
     */
    Duration getNextDelay(Notification notification);
}
