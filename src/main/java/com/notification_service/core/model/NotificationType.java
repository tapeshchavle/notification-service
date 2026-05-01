package com.notification_service.core.model;

/**
 * Defines all supported notification delivery channels.
 * <p>
 * To add a new channel (e.g., WHATSAPP), simply:
 * 1. Add the enum constant here
 * 2. Create a ChannelProcessor implementation annotated with @Component
 * 3. The ChannelProcessorRegistry auto-discovers it — zero changes elsewhere
 */
public enum NotificationType {

    EMAIL,
    SMS,
    PUSH,
    IN_APP
}
