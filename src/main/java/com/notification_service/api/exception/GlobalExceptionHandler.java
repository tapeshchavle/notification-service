package com.notification_service.api.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for uniform error responses across all API endpoints.
 * <p>
 * Produces consistent JSON error responses with structure:
 * <pre>
 * {
 *   "timestamp": "2024-01-01T00:00:00Z",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "errorCode": "INVALID_REQUEST",
 *   "message": "Validation failed",
 *   "details": { ... }
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotificationException.class)
    public ResponseEntity<Map<String, Object>> handleNotificationException(NotificationException e) {
        log.error("NotificationException: {} [{}]", e.getMessage(), e.getErrorCode());

        HttpStatus status = mapErrorCodeToStatus(e.getErrorCode());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("errorCode", e.getErrorCode().name());
        body.put("message", e.getMessage());

        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException e) {

        String details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation failed: {}", details);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("errorCode", "VALIDATION_FAILED");
        body.put("message", "Request validation failed");
        body.put("details", details);

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("IllegalArgumentException: {}", e.getMessage());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("errorCode", "INVALID_REQUEST");
        body.put("message", e.getMessage());

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        log.error("IllegalStateException: {}", e.getMessage());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        body.put("error", "Service Unavailable");
        body.put("errorCode", "QUEUE_FULL");
        body.put("message", e.getMessage());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("Unhandled exception", e);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("errorCode", "INTERNAL_ERROR");
        body.put("message", "An unexpected error occurred");

        return ResponseEntity.internalServerError().body(body);
    }

    private HttpStatus mapErrorCodeToStatus(NotificationException.ErrorCode code) {
        return switch (code) {
            case NOTIFICATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CHANNEL_DISABLED, QUIET_HOURS_ACTIVE, INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case QUEUE_FULL -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
