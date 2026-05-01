package com.notification_service.channel;

import com.notification_service.core.model.Notification;
import com.notification_service.core.model.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Template Method base class for all channel processors.
 * <p>
 * Provides common cross-cutting concerns:
 * <ul>
 *   <li>Structured logging (before/after/error)</li>
 *   <li>Timing metrics</li>
 *   <li>Exception wrapping into {@link ChannelDeliveryException}</li>
 * </ul>
 * <p>
 * Subclasses only implement {@link #doSend(Notification)} with their channel-specific logic.
 */
public abstract class AbstractChannelProcessor implements ChannelProcessor {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    private final NotificationType type;

    protected AbstractChannelProcessor(NotificationType type) {
        this.type = type;
    }

    @Override
    public final NotificationType getType() {
        return type;
    }

    @Override
    public final boolean supports(NotificationType type) {
        return this.type == type;
    }

    /**
     * Template method: wraps {@link #doSend(Notification)} with logging, timing, and error handling.
     */
    @Override
    public final void process(Notification notification) {
        log.info("[{}] Processing notification: id={}, userId={}, priority={}",
                type, notification.getId(), notification.getUserId(), notification.getPriority());

        long startTime = System.currentTimeMillis();

        try {
            doSend(notification);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[{}] Successfully delivered notification: id={} in {}ms",
                    type, notification.getId(), elapsed);
        } catch (ChannelDeliveryException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[{}] Delivery failed for notification: id={} after {}ms — {}",
                    type, notification.getId(), elapsed, e.getMessage());
            throw e;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[{}] Unexpected error for notification: id={} after {}ms",
                    type, notification.getId(), elapsed, e);
            throw new ChannelDeliveryException(
                    "Unexpected error in " + type + " processor: " + e.getMessage(), e, true);
        }
    }

    /**
     * Channel-specific delivery logic. Subclasses must implement this.
     *
     * @param notification the notification to deliver
     * @throws ChannelDeliveryException if delivery fails
     */
    protected abstract void doSend(Notification notification);
}
