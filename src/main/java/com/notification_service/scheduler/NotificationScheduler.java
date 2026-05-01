package com.notification_service.scheduler;

import com.notification_service.core.model.Notification;
import com.notification_service.core.model.NotificationPriority;
import com.notification_service.core.model.NotificationType;
import com.notification_service.core.service.NotificationOrchestrator;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Scheduler Service for delayed and scheduled notifications.
 * <p>
 * Allows external services to schedule notifications for future delivery.
 * At the scheduled time, the notification is handed off to the
 * {@link NotificationOrchestrator} for standard processing.
 * <p>
 * This corresponds to the "Scheduler Service" box in the architecture diagram,
 * with its own dedicated database/store for pending scheduled items.
 * <p>
 * Features:
 * <ul>
 *   <li>Schedule notifications for a specific {@link Instant}</li>
 *   <li>Cancel scheduled notifications before they fire</li>
 *   <li>In-memory tracking of scheduled tasks</li>
 * </ul>
 */
@Service
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final NotificationOrchestrator orchestrator;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final AtomicLong totalScheduled = new AtomicLong(0);
    private final AtomicLong totalFired = new AtomicLong(0);
    private final AtomicLong totalCancelled = new AtomicLong(0);

    public NotificationScheduler(NotificationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setName("notif-scheduler-" + t.getId());
            t.setDaemon(true);
            return t;
        });
        log.info("NotificationScheduler initialized");
    }

    /**
     * Schedules a notification for delivery at a specific time.
     *
     * @param userId      target user
     * @param type        delivery channel
     * @param subject     notification subject
     * @param body        notification body
     * @param metadata    channel-specific metadata
     * @param priority    delivery priority
     * @param scheduledAt when to deliver the notification
     * @return a schedule ID that can be used to cancel
     */
    public String scheduleNotification(String userId, NotificationType type, String subject,
                                        String body, Map<String, String> metadata,
                                        NotificationPriority priority, Instant scheduledAt) {

        Duration delay = Duration.between(Instant.now(), scheduledAt);
        if (delay.isNegative() || delay.isZero()) {
            log.info("Scheduled time is in the past — sending immediately");
            Notification notification = orchestrator.sendNotification(
                    userId, type, subject, body, metadata, priority);
            return notification.getId();
        }

        String scheduleId = "schedule-" + java.util.UUID.randomUUID().toString().substring(0, 8);

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                log.info("Firing scheduled notification: scheduleId={}, userId={}, type={}",
                        scheduleId, userId, type);
                orchestrator.sendNotification(userId, type, subject, body, metadata, priority);
                totalFired.incrementAndGet();
            } catch (Exception e) {
                log.error("Failed to fire scheduled notification: scheduleId={}", scheduleId, e);
            } finally {
                scheduledTasks.remove(scheduleId);
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);

        scheduledTasks.put(scheduleId, future);
        totalScheduled.incrementAndGet();

        log.info("Notification scheduled: scheduleId={} | userId={} | type={} | fires in {}s",
                scheduleId, userId, type, delay.getSeconds());

        return scheduleId;
    }

    /**
     * Cancels a scheduled notification.
     *
     * @param scheduleId the schedule ID returned by {@link #scheduleNotification}
     * @return true if the notification was cancelled, false if not found or already fired
     */
    public boolean cancelScheduledNotification(String scheduleId) {
        ScheduledFuture<?> future = scheduledTasks.remove(scheduleId);
        if (future != null && !future.isDone()) {
            boolean cancelled = future.cancel(false);
            if (cancelled) {
                totalCancelled.incrementAndGet();
                log.info("Cancelled scheduled notification: {}", scheduleId);
            }
            return cancelled;
        }
        log.warn("Scheduled notification not found or already fired: {}", scheduleId);
        return false;
    }

    /**
     * Returns the number of currently pending scheduled notifications.
     */
    public int getPendingCount() {
        return scheduledTasks.size();
    }

    /**
     * Returns scheduler metrics.
     */
    public Map<String, Long> getMetrics() {
        return Map.of(
                "totalScheduled", totalScheduled.get(),
                "totalFired", totalFired.get(),
                "totalCancelled", totalCancelled.get(),
                "currentPending", (long) scheduledTasks.size()
        );
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down NotificationScheduler...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("NotificationScheduler shutdown complete. Stats: scheduled={}, fired={}, cancelled={}",
                totalScheduled.get(), totalFired.get(), totalCancelled.get());
    }
}
