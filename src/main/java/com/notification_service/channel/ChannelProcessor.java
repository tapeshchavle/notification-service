package com.notification_service.channel;

import com.notification_service.core.model.Notification;
import com.notification_service.core.model.NotificationType;

/**
 * Strategy interface for notification channel processors.
 * <p>
 * Each implementation handles delivery for a specific channel (Email, SMS, Push, In-App).
 * New channels are added by implementing this interface and annotating with {@code @Component} —
 * the {@link ChannelProcessorRegistry} auto-discovers them at startup.
 *
 * <h3>Adding a New Channel</h3>
 * <pre>
 * {@code @Component}
 * public class WhatsAppChannelProcessor extends AbstractChannelProcessor {
 *     public WhatsAppChannelProcessor() { super(NotificationType.WHATSAPP); }
 *     protected void doSend(Notification notification) { ... }
 * }
 * </pre>
 *
 * @see AbstractChannelProcessor
 * @see ChannelProcessorRegistry
 */
public interface ChannelProcessor {

    /**
     * Returns the notification type this processor handles.
     */
    NotificationType getType();

    /**
     * Checks if this processor supports the given notification type.
     */
    boolean supports(NotificationType type);

    /**
     * Processes and delivers the notification.
     *
     * @param notification the notification to deliver
     * @throws ChannelDeliveryException if delivery fails
     */
    void process(Notification notification);
}
