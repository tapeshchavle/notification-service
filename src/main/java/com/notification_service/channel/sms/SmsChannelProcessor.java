package com.notification_service.channel.sms;

import com.notification_service.channel.AbstractChannelProcessor;
import com.notification_service.channel.ChannelDeliveryException;
import com.notification_service.core.model.Notification;
import com.notification_service.core.model.NotificationType;
import org.springframework.stereotype.Component;

/**
 * SMS channel processor.
 * <p>
 * In production, this would integrate with:
 * - Twilio SMS API
 * - AWS SNS
 * - Vonage (Nexmo)
 * <p>
 * Currently simulates SMS delivery with structured logging.
 */
@Component
public class SmsChannelProcessor extends AbstractChannelProcessor {

    private static final int SMS_MAX_LENGTH = 160;

    public SmsChannelProcessor() {
        super(NotificationType.SMS);
    }

    @Override
    protected void doSend(Notification notification) {
        String phoneNumber = notification.getMetadata().get("phoneNumber");
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new ChannelDeliveryException(
                    "Missing 'phoneNumber' in metadata for notification: " + notification.getId(),
                    false
            );
        }

        // Truncate body for SMS if needed
        String smsBody = notification.getBody();
        if (smsBody.length() > SMS_MAX_LENGTH) {
            smsBody = smsBody.substring(0, SMS_MAX_LENGTH - 3) + "...";
            log.warn("[SMS] Message truncated to {} chars for notification: {}",
                    SMS_MAX_LENGTH, notification.getId());
        }

        log.info("[SMS] Sending SMS to: {} | Body: '{}'", phoneNumber, smsBody);

        // Simulate transient failure for testing
        simulateTransientFailure(notification);

        log.info("[SMS] SMS delivered successfully to: {}", phoneNumber);
    }

    private void simulateTransientFailure(Notification notification) {
        if (notification.getMetadata().containsKey("simulateFailure")) {
            throw new ChannelDeliveryException(
                    "Simulated SMS gateway timeout for notification: " + notification.getId(),
                    true
            );
        }
    }
}
