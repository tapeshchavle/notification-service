package com.notification_service.api.exception;

/**
 * Custom runtime exception for notification service errors.
 * Carries an error code for machine-readable error classification.
 */
public class NotificationException extends RuntimeException {

    private final ErrorCode errorCode;

    public NotificationException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public NotificationException(String message, Throwable cause, ErrorCode errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Machine-readable error codes for API consumers.
     */
    public enum ErrorCode {
        NOTIFICATION_NOT_FOUND,
        CHANNEL_DISABLED,
        QUIET_HOURS_ACTIVE,
        QUEUE_FULL,
        INVALID_REQUEST,
        INTERNAL_ERROR
    }
}
