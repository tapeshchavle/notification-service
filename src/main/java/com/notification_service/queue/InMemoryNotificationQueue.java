package com.notification_service.queue;

import com.notification_service.core.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory notification queue backed by {@link PriorityBlockingQueue}.
 * <p>
 * Features:
 * <ul>
 *   <li>Priority-aware: CRITICAL notifications dequeue before LOW</li>
 *   <li>Thread-safe: safe for concurrent producer/consumer access</li>
 *   <li>Blocking consumer: {@link #dequeue()} blocks until a notification is available</li>
 *   <li>Bounded capacity: rejects when queue is full (backpressure)</li>
 * </ul>
 * <p>
 * In production, replace with RabbitMQ, Kafka, or AWS SQS by implementing {@link NotificationQueue}.
 */
@Component
public class InMemoryNotificationQueue implements NotificationQueue {

    private static final Logger log = LoggerFactory.getLogger(InMemoryNotificationQueue.class);

    private final PriorityBlockingQueue<Notification> queue;
    private final int capacity;
    private final AtomicLong totalEnqueued = new AtomicLong(0);
    private final AtomicLong totalDequeued = new AtomicLong(0);

    public InMemoryNotificationQueue(
            @Value("${notification.queue.capacity:10000}") int capacity) {
        this.capacity = capacity;
        this.queue = new PriorityBlockingQueue<>(Math.min(capacity, 1000));
        log.info("InMemoryNotificationQueue initialized with capacity: {}", capacity);
    }

    @Override
    public void enqueue(Notification notification) {
        if (queue.size() >= capacity) {
            throw new IllegalStateException(
                    "Notification queue is full (capacity=" + capacity + "). " +
                    "Notification " + notification.getId() + " rejected.");
        }

        notification.markQueued();
        queue.offer(notification);
        long count = totalEnqueued.incrementAndGet();

        log.debug("Enqueued notification: {} | Priority: {} | Queue size: {} | Total enqueued: {}",
                notification.getId(), notification.getPriority(), queue.size(), count);
    }

    @Override
    public Notification dequeue() throws InterruptedException {
        Notification notification = queue.take(); // Blocks until available
        totalDequeued.incrementAndGet();
        return notification;
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Returns queue statistics for monitoring.
     */
    public String getStats() {
        return String.format("Queue[size=%d, capacity=%d, totalEnqueued=%d, totalDequeued=%d]",
                queue.size(), capacity, totalEnqueued.get(), totalDequeued.get());
    }
}
