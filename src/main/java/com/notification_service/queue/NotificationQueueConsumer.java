package com.notification_service.queue;

import com.notification_service.channel.ChannelDeliveryException;
import com.notification_service.channel.ChannelProcessor;
import com.notification_service.channel.ChannelProcessorRegistry;
import com.notification_service.core.model.Notification;
import com.notification_service.core.model.NotificationStatus;
import com.notification_service.retry.RetryPolicy;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Queue consumer that continuously polls the {@link NotificationQueue} and dispatches
 * notifications to the appropriate {@link ChannelProcessor} via the {@link ChannelProcessorRegistry}.
 * <p>
 * Features:
 * <ul>
 *   <li>Multi-threaded consumption (configurable thread count)</li>
 *   <li>Automatic retry with exponential backoff via {@link RetryPolicy}</li>
 *   <li>Dead-letter handling for permanently failed notifications</li>
 *   <li>In-memory notification tracking for status queries</li>
 *   <li>Graceful shutdown with drain timeout</li>
 * </ul>
 *
 * <h3>Retry Flow</h3>
 * <pre>
 *   dequeue → process → SUCCESS → mark SENT
 *                     → FAILURE → shouldRetry?
 *                                  → YES → schedule re-enqueue after backoff delay
 *                                  → NO  → mark DEAD_LETTERED
 * </pre>
 */
@Component
public class NotificationQueueConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationQueueConsumer.class);

    private final NotificationQueue notificationQueue;
    private final ChannelProcessorRegistry channelProcessorRegistry;
    private final RetryPolicy retryPolicy;
    private final int consumerThreads;

    private ExecutorService consumerExecutor;
    private ScheduledExecutorService retryScheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // ── Metrics ────────────────────────────────────────────────────────
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalSucceeded = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final AtomicLong totalRetried = new AtomicLong(0);
    private final AtomicLong totalDeadLettered = new AtomicLong(0);

    // ── In-memory notification tracker (for status API) ────────────────
    private final Map<String, Notification> notificationTracker = new ConcurrentHashMap<>();

    public NotificationQueueConsumer(
            NotificationQueue notificationQueue,
            ChannelProcessorRegistry channelProcessorRegistry,
            RetryPolicy retryPolicy,
            @Value("${notification.queue.consumer-threads:4}") int consumerThreads) {
        this.notificationQueue = notificationQueue;
        this.channelProcessorRegistry = channelProcessorRegistry;
        this.retryPolicy = retryPolicy;
        this.consumerThreads = consumerThreads;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────

    @PostConstruct
    public void start() {
        running.set(true);

        consumerExecutor = Executors.newFixedThreadPool(consumerThreads, r -> {
            Thread t = new Thread(r);
            t.setName("notif-consumer-" + t.getId());
            t.setDaemon(true);
            return t;
        });

        retryScheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setName("notif-retry-" + t.getId());
            t.setDaemon(true);
            return t;
        });

        for (int i = 0; i < consumerThreads; i++) {
            consumerExecutor.submit(this::consumeLoop);
        }

        log.info("NotificationQueueConsumer started with {} consumer threads", consumerThreads);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        log.info("Shutting down NotificationQueueConsumer...");

        consumerExecutor.shutdownNow();
        retryScheduler.shutdown();

        try {
            if (!retryScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                retryScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("NotificationQueueConsumer shutdown complete. Stats: processed={}, succeeded={}, " +
                 "failed={}, retried={}, deadLettered={}",
                totalProcessed.get(), totalSucceeded.get(), totalFailed.get(),
                totalRetried.get(), totalDeadLettered.get());
    }

    // ── Consumer Loop ──────────────────────────────────────────────────

    private void consumeLoop() {
        log.info("Consumer thread started: {}", Thread.currentThread().getName());

        while (running.get()) {
            try {
                Notification notification = notificationQueue.dequeue();
                processNotification(notification);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Consumer thread interrupted: {}", Thread.currentThread().getName());
                break;
            } catch (Exception e) {
                log.error("Unexpected error in consumer loop", e);
            }
        }

        log.info("Consumer thread stopped: {}", Thread.currentThread().getName());
    }

    /**
     * Processes a single notification: resolve channel → invoke processor → handle result.
     */
    private void processNotification(Notification notification) {
        totalProcessed.incrementAndGet();
        notification.markProcessing();
        notificationTracker.put(notification.getId(), notification);

        try {
            ChannelProcessor processor = channelProcessorRegistry.getProcessor(notification.getType());
            processor.process(notification);

            // ── Success ────────────────────────────────────────────────
            notification.markSent();
            totalSucceeded.incrementAndGet();
            log.info("Notification delivered: {} via {} (attempt {})",
                    notification.getId(), notification.getType(),
                    notification.getRetryCount() + 1);

        } catch (ChannelDeliveryException e) {
            handleDeliveryFailure(notification, e);
        } catch (Exception e) {
            handleDeliveryFailure(notification,
                    new ChannelDeliveryException("Unexpected error: " + e.getMessage(), e, true));
        }
    }

    /**
     * Handles delivery failure: retry with backoff or dead-letter.
     */
    private void handleDeliveryFailure(Notification notification, ChannelDeliveryException e) {
        notification.markFailed(e.getMessage());
        totalFailed.incrementAndGet();

        if (!e.isRetryable()) {
            log.error("Non-retryable failure for notification: {} — dead-lettering",
                    notification.getId());
            notification.markDeadLettered();
            totalDeadLettered.incrementAndGet();
            return;
        }

        if (retryPolicy.shouldRetry(notification)) {
            Duration delay = retryPolicy.getNextDelay(notification);
            Instant nextRetry = Instant.now().plus(delay);
            notification.markRetryScheduled(nextRetry);
            totalRetried.incrementAndGet();

            log.warn("Scheduling retry for notification: {} | Attempt: {}/{} | Delay: {}ms | Reason: {}",
                    notification.getId(), notification.getRetryCount(),
                    notification.getMaxRetries(), delay.toMillis(), e.getMessage());

            // Schedule re-enqueue after the backoff delay
            retryScheduler.schedule(() -> {
                try {
                    log.info("Re-enqueueing notification: {} (retry attempt {})",
                            notification.getId(), notification.getRetryCount());
                    notificationQueue.enqueue(notification);
                } catch (Exception retryError) {
                    log.error("Failed to re-enqueue notification: {} — dead-lettering",
                            notification.getId(), retryError);
                    notification.markDeadLettered();
                    totalDeadLettered.incrementAndGet();
                }
            }, delay.toMillis(), TimeUnit.MILLISECONDS);

        } else {
            log.error("Max retries exhausted for notification: {} — dead-lettering",
                    notification.getId());
            notification.markDeadLettered();
            totalDeadLettered.incrementAndGet();
        }
    }

    // ── Query Methods (for Status API) ─────────────────────────────────

    /**
     * Retrieves a notification by its ID.
     */
    public Notification getNotification(String notificationId) {
        return notificationTracker.get(notificationId);
    }

    /**
     * Returns current consumer metrics.
     */
    public Map<String, Long> getMetrics() {
        return Map.of(
                "totalProcessed", totalProcessed.get(),
                "totalSucceeded", totalSucceeded.get(),
                "totalFailed", totalFailed.get(),
                "totalRetried", totalRetried.get(),
                "totalDeadLettered", totalDeadLettered.get(),
                "queueSize", (long) notificationQueue.size()
        );
    }
}
