package com.notification_service.queue;

import com.notification_service.core.model.Notification;

/**
 * Abstraction for the notification queue.
 * <p>
 * Decouples the producer (Orchestrator) from the consumer (QueueConsumer).
 * Default implementation uses an in-memory {@link java.util.concurrent.PriorityBlockingQueue}.
 * In production, this could be swapped for RabbitMQ, Kafka, or SQS.
 *
 * @see InMemoryNotificationQueue
 */
public interface NotificationQueue {

    /**
     * Adds a notification to the queue.
     *
     * @param notification the notification to enqueue
     * @throws IllegalStateException if the queue is at capacity
     */
    void enqueue(Notification notification);

    /**
     * Retrieves and removes the head of the queue, blocking if empty.
     *
     * @return the highest-priority notification
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    Notification dequeue() throws InterruptedException;

    /**
     * Returns the current number of notifications in the queue.
     */
    int size();

    /**
     * Checks if the queue is empty.
     */
    boolean isEmpty();
}
