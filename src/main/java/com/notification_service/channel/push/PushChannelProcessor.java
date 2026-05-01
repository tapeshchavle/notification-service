package com.notification_service.channel.push;

import com.notification_service.channel.AbstractChannelProcessor;
import com.notification_service.channel.ChannelDeliveryException;
import com.notification_service.core.model.Notification;
import com.notification_service.core.model.NotificationType;
import org.springframework.stereotype.Component;

/**
 * Push notification channel processor.
 * <p>
 * In production, this would integrate with:
 * - Firebase Cloud Messaging (FCM)
 * - Apple Push Notification Service (APNs)
 * - Amazon SNS for mobile push
 * <p>
 * Currently simulates push delivery with structured logging.
 */
@Component
public class PushChannelProcessor extends AbstractChannelProcessor {

    public PushChannelProcessor() {
        super(NotificationType.PUSH);
    }

    @Override
    protected void doSend(Notification notification) {
        String deviceToken = notification.getMetadata().get("deviceToken");
        if (deviceToken == null || deviceToken.isBlank()) {
            throw new ChannelDeliveryException(
                    "Missing 'deviceToken' in metadata for notification: " + notification.getId(),
                    false
            );
        }

        String platform = notification.getMetadata().getOrDefault("platform", "UNKNOWN");

        log.info("[PUSH] Sending push notification | Platform: {} | DeviceToken: {}... | Title: '{}'",
                platform, deviceToken.substring(0, Math.min(8, deviceToken.length())),
                notification.getSubject());

        // Simulate transient failure for testing
        simulateTransientFailure(notification);

        log.info("[PUSH] Push notification delivered successfully | DeviceToken: {}...",
                deviceToken.substring(0, Math.min(8, deviceToken.length())));
    }

    private void simulateTransientFailure(Notification notification) {
        if (notification.getMetadata().containsKey("simulateFailure")) {
            throw new ChannelDeliveryException(
                    "Simulated FCM unavailable for notification: " + notification.getId(),
                    true
            );
        }
    }
}
