package com.notification_service.api.controller;

import com.notification_service.api.dto.NotificationRequest;
import com.notification_service.api.dto.NotificationResponse;
import com.notification_service.api.dto.ScheduleNotificationRequest;
import com.notification_service.core.model.Notification;
import com.notification_service.core.service.NotificationOrchestrator;
import com.notification_service.queue.NotificationQueueConsumer;
import com.notification_service.scheduler.NotificationScheduler;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API controller for the Notification Service.
 * <p>
 * This is the entry point for external services (Service 1, Service 2 in the architecture)
 * that sit behind the load balancer. All endpoints are versioned under {@code /api/v1/notifications}.
 * <p>
 * Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/notifications} — Send an immediate notification</li>
 *   <li>{@code POST /api/v1/notifications/schedule} — Schedule a notification for later</li>
 *   <li>{@code GET /api/v1/notifications/{id}/status} — Check notification delivery status</li>
 *   <li>{@code GET /api/v1/notifications/metrics} — View system metrics</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationOrchestrator orchestrator;
    private final NotificationScheduler scheduler;
    private final NotificationQueueConsumer queueConsumer;

    public NotificationController(NotificationOrchestrator orchestrator,
                                   NotificationScheduler scheduler,
                                   NotificationQueueConsumer queueConsumer) {
        this.orchestrator = orchestrator;
        this.scheduler = scheduler;
        this.queueConsumer = queueConsumer;
    }

    // ── Send Immediate Notification ────────────────────────────────────

    /**
     * Sends a notification immediately.
     * The notification is validated, checked against user preferences,
     * and enqueued for async delivery via the appropriate channel.
     *
     * @param request the notification request
     * @return 202 Accepted with notification ID and status
     */
    @PostMapping
    public ResponseEntity<NotificationResponse> sendNotification(
            @Valid @RequestBody NotificationRequest request) {

        log.info("POST /api/v1/notifications — userId={}, type={}, priority={}",
                request.getUserId(), request.getType(), request.getPriority());

        Notification notification = orchestrator.sendNotification(
                request.getUserId(),
                request.getType(),
                request.getSubject(),
                request.getBody(),
                request.getMetadata(),
                request.getPriority()
        );

        NotificationResponse response = NotificationResponse.success(
                notification.getId(), notification.getType());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // ── Schedule Notification ──────────────────────────────────────────

    /**
     * Schedules a notification for delivery at a future time.
     *
     * @param request the schedule request with ISO-8601 timestamp
     * @return 202 Accepted with schedule ID
     */
    @PostMapping("/schedule")
    public ResponseEntity<NotificationResponse> scheduleNotification(
            @Valid @RequestBody ScheduleNotificationRequest request) {

        log.info("POST /api/v1/notifications/schedule — userId={}, type={}, scheduledAt={}",
                request.getUserId(), request.getType(), request.getScheduledAt());

        String scheduleId = scheduler.scheduleNotification(
                request.getUserId(),
                request.getType(),
                request.getSubject(),
                request.getBody(),
                request.getMetadata(),
                request.getPriority(),
                request.getScheduledAt()
        );

        NotificationResponse response = NotificationResponse.scheduled(
                scheduleId, request.getType(), request.getScheduledAt());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // ── Check Status ───────────────────────────────────────────────────

    /**
     * Retrieves the current status of a notification by its ID.
     *
     * @param id the notification ID
     * @return 200 OK with status, or 404 if not found
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<NotificationResponse> getNotificationStatus(@PathVariable String id) {
        log.info("GET /api/v1/notifications/{}/status", id);

        Notification notification = queueConsumer.getNotification(id);
        if (notification == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(NotificationResponse.error("Notification not found: " + id));
        }

        NotificationResponse response = NotificationResponse.fromNotification(
                notification.getId(), notification.getStatus(), notification.getType());

        return ResponseEntity.ok(response);
    }

    // ── Metrics ────────────────────────────────────────────────────────

    /**
     * Returns current system metrics (queue size, success/failure counts, etc.).
     * Useful for monitoring and alerting.
     */
    @GetMapping("/metrics")
    public ResponseEntity<NotificationResponse> getMetrics() {
        log.debug("GET /api/v1/notifications/metrics");

        Map<String, Long> metrics = queueConsumer.getMetrics();
        return ResponseEntity.ok(NotificationResponse.withMetrics(metrics));
    }
}
