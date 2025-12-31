package org.milkteamc.autotreechop.translation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles MiniMessage parsing with placeholder support and caching
 */
public class MessageFormatter {

    private final MiniMessage miniMessage;
    private final StyleRegistry styleRegistry;
    private final Map<String, Component> cachedMessages = new ConcurrentHashMap<>();

    public MessageFormatter(StyleRegistry styleRegistry) {
        this.styleRegistry = styleRegistry;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Formats a message with style tags and placeholders
     *
     * @param message The raw message from properties file
     * @param resolvers TagResolvers for placeholder replacement
     * @return Formatted Adventure Component
     */
    public Component format(String message, TagResolver... resolvers) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        // Apply custom style tags first
        String styledMessage = styleRegistry.applyStyles(message);

        // Parse with MiniMessage and resolvers
        if (resolvers != null && resolvers.length > 0) {
            // Combine all resolvers
            TagResolver combined = TagResolver.resolver(resolvers);
            return miniMessage.deserialize(styledMessage, combined);
        }

        // Check cache for messages without resolvers
        return cachedMessages.computeIfAbsent(styledMessage, miniMessage::deserialize);
    }

    /**
     * Formats a message with a single tag resolver
     *
     * @param message The raw message
     * @param resolver Single TagResolver
     * @return Formatted Component
     */
    public Component format(String message, TagResolver resolver) {
        return format(message, new TagResolver[]{resolver});
    }

    /**
     * Clears the message cache (useful for reloads)
     */
    public void clearCache() {
        cachedMessages.clear();
    }

    /**
     * Gets the underlying MiniMessage instance
     *
     * @return MiniMessage instance
     */
    public MiniMessage getMiniMessage() {
        return miniMessage;
    }
}