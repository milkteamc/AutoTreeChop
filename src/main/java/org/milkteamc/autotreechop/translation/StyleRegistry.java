package org.milkteamc.autotreechop.translation;

import org.milkteamc.autotreechop.AutoTreeChop;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages custom style tags from styles.properties and converts them to MiniMessage format
 */
public class StyleRegistry {

    private final AutoTreeChop plugin;
    private final Map<String, String> styles = new ConcurrentHashMap<>();
    private final File stylesFile;

    public StyleRegistry(AutoTreeChop plugin) {
        this.plugin = plugin;
        this.stylesFile = new File(plugin.getDataFolder(), "lang/styles.properties");
    }

    /**
     * Loads styles from styles.properties file
     */
    public void loadStyles() {
        styles.clear();

        if (!stylesFile.exists()) {
            plugin.getLogger().warning("styles.properties not found at: " + stylesFile.getAbsolutePath());
            plugin.getLogger().warning("Using default styles");
            loadDefaultStyles();
            return;
        }

        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(stylesFile), StandardCharsets.UTF_8)) {
            properties.load(reader);

            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                styles.put(key, value);
            }

            // Ensure minimum required styles exist
            if (!styles.containsKey("prefix")) {
                plugin.getLogger().warning("Missing 'prefix' style, adding default");
                styles.put("prefix", "<green>");
            }
            if (!styles.containsKey("prefix_negative")) {
                plugin.getLogger().warning("Missing 'prefix_negative' style, adding default");
                styles.put("prefix_negative", "<red>");
            }

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load styles.properties", e);
            loadDefaultStyles();
        }
    }

    /**
     * Loads default styles if styles.properties is missing or fails to load
     */
    private void loadDefaultStyles() {
        // Default MiniMessage format styles
        styles.put("prefix", "<green>");
        styles.put("prefix_negative", "<red>");
        styles.put("text", "<green>");
        styles.put("positive", "<green>");
        styles.put("negative", "<red>");
        plugin.getLogger().info("Loaded default styles");
    }

    /**
     * Applies style replacements to a message string
     * Converts custom tags like <prefix>text</prefix> to their MiniMessage equivalents
     * Handles the {slot} placeholder used by TinyTranslations
     * Also converts {placeholder} to <placeholder> for MiniMessage compatibility
     *
     * @param message The message with custom style tags
     * @return The message with MiniMessage formatting
     */
    public String applyStyles(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String result = message;

        // First, convert {placeholder} to <placeholder> for MiniMessage compatibility
        // But preserve {slot} for now as it's used in style definitions
        result = convertPlaceholders(result);

        // Process each style tag
        for (Map.Entry<String, String> entry : styles.entrySet()) {
            String tag = entry.getKey();
            String styleValue = entry.getValue();

            String openTag = "<" + tag + ">";
            String closeTag = "</" + tag + ">";

            // Find and replace all occurrences of this tag pair
            while (result.contains(openTag) && result.contains(closeTag)) {
                int startIdx = result.indexOf(openTag);
                int endIdx = result.indexOf(closeTag, startIdx);

                if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) {
                    break;
                }

                // Extract the content between the tags
                String content = result.substring(startIdx + openTag.length(), endIdx);

                // Apply the style by replacing {slot} with the actual content
                String styledContent = applyStyleToContent(styleValue, content);

                // Replace the entire tag pair with the styled content
                result = result.substring(0, startIdx) + styledContent + result.substring(endIdx + closeTag.length());
            }
        }

        return result;
    }

    /**
     * Converts {placeholder} format to <placeholder> format for MiniMessage
     * Preserves {slot} as it's used in style definitions
     */
    private String convertPlaceholders(String message) {
        if (message == null || !message.contains("{")) {
            return message;
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < message.length()) {
            if (message.charAt(i) == '{') {
                int end = message.indexOf('}', i);
                if (end != -1) {
                    String placeholder = message.substring(i + 1, end);
                    // Don't convert {slot} as it's used in styles
                    if (!"slot".equals(placeholder)) {
                        result.append('<').append(placeholder).append('>');
                    } else {
                        result.append('{').append(placeholder).append('}');
                    }
                    i = end + 1;
                } else {
                    result.append(message.charAt(i));
                    i++;
                }
            } else {
                result.append(message.charAt(i));
                i++;
            }
        }

        return result.toString();
    }

    /**
     * Applies a style to content by replacing {slot} with the actual content
     * Also recursively processes nested style tags
     */
    private String applyStyleToContent(String styleValue, String content) {
        // First, recursively process any nested style tags in the content
        String processedContent = content;
        for (Map.Entry<String, String> entry : styles.entrySet()) {
            String tag = entry.getKey();
            String nestedStyle = entry.getValue();

            String openTag = "<" + tag + ">";
            String closeTag = "</" + tag + ">";

            while (processedContent.contains(openTag) && processedContent.contains(closeTag)) {
                int startIdx = processedContent.indexOf(openTag);
                int endIdx = processedContent.indexOf(closeTag, startIdx);

                if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) {
                    break;
                }

                String innerContent = processedContent.substring(startIdx + openTag.length(), endIdx);
                String styledInner = applyStyleToContent(nestedStyle, innerContent);

                processedContent = processedContent.substring(0, startIdx) + styledInner + processedContent.substring(endIdx + closeTag.length());
            }
        }

        // Now replace {slot} in the style value with the processed content
        if (styleValue.contains("{slot}")) {
            return styleValue.replace("{slot}", processedContent);
        } else {
            // If no {slot}, just wrap the content with the style
            return styleValue + processedContent + "<reset>";
        }
    }

    /**
     * Gets a style value by key
     *
     * @param key The style key
     * @return The MiniMessage format style, or null if not found
     */
    public String getStyle(String key) {
        return styles.get(key);
    }

    /**
     * Checks if a style exists
     *
     * @param key The style key
     * @return true if the style exists
     */
    public boolean hasStyle(String key) {
        return styles.containsKey(key);
    }

    /**
     * Reloads styles from file
     */
    public void reload() {
        loadStyles();
    }

    /**
     * Gets all loaded styles (for debugging)
     */
    public Map<String, String> getAllStyles() {
        return new ConcurrentHashMap<>(styles);
    }
}