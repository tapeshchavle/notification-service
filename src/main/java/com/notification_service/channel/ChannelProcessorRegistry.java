package com.notification_service.channel;

import com.notification_service.core.model.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry (Factory) that auto-discovers all {@link ChannelProcessor} beans and provides
 * O(1) lookup by {@link NotificationType}.
 * <p>
 * Spring auto-injects all {@code ChannelProcessor} implementations via constructor injection.
 * At startup, the registry indexes them by type for fast lookup.
 * <p>
 * <strong>Open/Closed Principle:</strong> Adding a new channel requires zero changes here —
 * just implement {@link ChannelProcessor}, annotate with {@code @Component}, and it's auto-registered.
 */
@Component
public class ChannelProcessorRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChannelProcessorRegistry.class);

    private final Map<NotificationType, ChannelProcessor> processorMap;

    /**
     * Constructor injection: Spring provides all ChannelProcessor beans automatically.
     */
    public ChannelProcessorRegistry(List<ChannelProcessor> processors) {
        this.processorMap = new EnumMap<>(NotificationType.class);

        for (ChannelProcessor processor : processors) {
            ChannelProcessor existing = processorMap.put(processor.getType(), processor);
            if (existing != null) {
                log.warn("Duplicate ChannelProcessor for type {}: {} replaced by {}",
                        processor.getType(), existing.getClass().getSimpleName(),
                        processor.getClass().getSimpleName());
            }
            log.info("Registered channel processor: {} → {}",
                    processor.getType(), processor.getClass().getSimpleName());
        }

        log.info("ChannelProcessorRegistry initialized with {} processors: {}",
                processorMap.size(), processorMap.keySet());
    }

    /**
     * Returns the processor for the given notification type.
     *
     * @param type the notification channel type
     * @return the matching processor
     * @throws IllegalArgumentException if no processor is registered for the type
     */
    public ChannelProcessor getProcessor(NotificationType type) {
        return Optional.ofNullable(processorMap.get(type))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No ChannelProcessor registered for type: " + type));
    }

    /**
     * Checks if a processor is registered for the given type.
     */
    public boolean hasProcessor(NotificationType type) {
        return processorMap.containsKey(type);
    }

    /**
     * Returns all registered notification types.
     */
    public java.util.Set<NotificationType> getRegisteredTypes() {
        return java.util.Collections.unmodifiableSet(processorMap.keySet());
    }
}
