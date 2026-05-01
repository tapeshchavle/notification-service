package com.notification_service.channel.email;

import com.notification_service.channel.AbstractChannelProcessor;
import com.notification_service.channel.ChannelDeliveryException;
import com.notification_service.core.model.Notification;
import com.notification_service.core.model.NotificationType;
import org.springframework.stereotype.Component;

/**
 * Email channel processor.
 * <p>
 * In production, this would integrate with:
 * - Spring JavaMailSender
 * - AWS SES
 * - SendGrid API
 * - Mailgun
 * <p>
 * Currently simulates email delivery with structured logging.
 */
@Component
public class EmailChannelProcessor extends AbstractChannelProcessor {

    public EmailChannelProcessor() {
        super(NotificationType.EMAIL);
    }

    @Override
    protected void doSend(Notification notification) {
        // Validate email-specific requirements
        String recipientEmail = notification.getMetadata().get("email");
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new ChannelDeliveryException(
                    "Missing 'email' in metadata for notification: " + notification.getId(),
                    false  // Non-retryable — data issue
            );
        }

        // Simulate email delivery
        log.info("[EMAIL] Sending email to: {} | Subject: '{}' | Body length: {} chars",
                recipientEmail, notification.getSubject(), notification.getBody().length());

        // Simulate occasional transient failures (for demo/testing retry mechanism)
        simulateTransientFailure(notification);

        log.info("[EMAIL] Email delivered successfully to: {}", recipientEmail);
    }

    /**
     * Simulates a transient failure ~10% of the time for testing retry behavior.
     * Remove this in production.
     */
    private void simulateTransientFailure(Notification notification) {
        if (notification.getMetadata().containsKey("simulateFailure")) {
            throw new ChannelDeliveryException(
                    "Simulated SMTP connection timeout for notification: " + notification.getId(),
                    true  // Retryable — transient issue
            );
        }
    }
}
