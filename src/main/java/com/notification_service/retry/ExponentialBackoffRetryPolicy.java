package com.notification_service.retry;

import com.notification_service.core.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff retry policy with jitter.
 * <p>
 * Calculates retry delays using the formula:
 * <pre>
 *   delay = min(baseDelay × multiplier^retryCount + jitter, maxDelay)
 * </pre>
 * <p>
 * Jitter prevents the "thundering herd" problem where many failed notifications
 * retry simultaneously and overwhelm the downstream service.
 * <p>
 * Example with defaults (base=1s, multiplier=2x, jitter=±20%):
 * <ul>
 *   <li>Retry 1: ~1.0s (0.8s - 1.2s)</li>
 *   <li>Retry 2: ~2.0s (1.6s - 2.4s)</li>
 *   <li>Retry 3: ~4.0s (3.2s - 4.8s)</li>
 *   <li>Retry 4: ~8.0s (6.4s - 9.6s)</li>
 * </ul>
 *
 * All values are externalized in {@code application.yml}.
 */
@Component
public class ExponentialBackoffRetryPolicy implements RetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(ExponentialBackoffRetryPolicy.class);

    private final int maxRetries;
    private final long baseDelayMs;
    private final double multiplier;
    private final long maxDelayMs;
    private final double jitterFactor;

    public ExponentialBackoffRetryPolicy(
            @Value("${notification.retry.max-retries:3}") int maxRetries,
            @Value("${notification.retry.base-delay-ms:1000}") long baseDelayMs,
            @Value("${notification.retry.multiplier:2.0}") double multiplier,
            @Value("${notification.retry.max-delay-ms:30000}") long maxDelayMs,
            @Value("${notification.retry.jitter-factor:0.2}") double jitterFactor) {
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.multiplier = multiplier;
        this.maxDelayMs = maxDelayMs;
        this.jitterFactor = jitterFactor;

        log.info("ExponentialBackoffRetryPolicy initialized: maxRetries={}, baseDelay={}ms, " +
                 "multiplier={}, maxDelay={}ms, jitter=±{}%",
                maxRetries, baseDelayMs, multiplier, maxDelayMs, (int)(jitterFactor * 100));
    }

    @Override
    public boolean shouldRetry(Notification notification) {
        boolean should = notification.getRetryCount() < maxRetries;
        if (!should) {
            log.warn("Max retries ({}) exhausted for notification: {} — moving to dead letter",
                    maxRetries, notification.getId());
        }
        return should;
    }

    @Override
    public Duration getNextDelay(Notification notification) {
        int attempt = notification.getRetryCount();

        // Calculate base exponential delay
        long exponentialDelay = (long) (baseDelayMs * Math.pow(multiplier, attempt));

        // Apply jitter: ±jitterFactor (e.g., ±20%)
        double jitter = 1.0 + ThreadLocalRandom.current().nextDouble(-jitterFactor, jitterFactor);
        long delayWithJitter = (long) (exponentialDelay * jitter);

        // Cap at maxDelay
        long finalDelay = Math.min(delayWithJitter, maxDelayMs);

        log.debug("Retry delay calculated for notification {} (attempt {}): " +
                  "base={}ms, exponential={}ms, withJitter={}ms, final={}ms",
                notification.getId(), attempt + 1,
                baseDelayMs, exponentialDelay, delayWithJitter, finalDelay);

        return Duration.ofMillis(finalDelay);
    }
}
