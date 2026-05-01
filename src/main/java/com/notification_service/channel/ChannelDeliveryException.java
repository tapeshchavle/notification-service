package com.notification_service.channel;

/**
 * Exception thrown when a channel processor fails to deliver a notification.
 * Used by the retry mechanism to determine if a retry should be attempted.
 */
public class ChannelDeliveryException extends RuntimeException {

    private final boolean retryable;

    public ChannelDeliveryException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public ChannelDeliveryException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    /**
     * Indicates whether this failure is transient and the notification should be retried.
     * Non-retryable failures (e.g., invalid recipient) go straight to dead letter.
     */
    public boolean isRetryable() {
        return retryable;
    }
}
