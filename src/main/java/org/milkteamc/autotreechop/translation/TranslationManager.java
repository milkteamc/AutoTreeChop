package org.milkteamc.autotreechop.translation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.AutoTreeChop;

/**
 * Manages translations and message sending with per-player locale support
 */
public class TranslationManager {

    private final AutoTreeChop plugin;
    private final StyleRegistry styleRegistry;
    private final MessageFormatter formatter;
    private final Map<Locale, Properties> translations = new ConcurrentHashMap<>();
    private final File langFolder;
    private BukkitAudiences adventure;

    private Locale defaultLocale;
    private boolean useClientLocale;

    public TranslationManager(AutoTreeChop plugin) {
        this.plugin = plugin;
        this.langFolder = new File(plugin.getDataFolder(), "lang");
        this.styleRegistry = new StyleRegistry(plugin);
        this.formatter = new MessageFormatter(styleRegistry);
        this.adventure = BukkitAudiences.create(plugin);
    }

    /**
     * Initializes the translation system
     */
    public void initialize(Locale defaultLocale, boolean useClientLocale) {
        this.defaultLocale = defaultLocale;
        this.useClientLocale = useClientLocale;

        // Load styles first
        styleRegistry.loadStyles();

        // Load all language files
        loadAllTranslations();
    }

    /**
     * Loads all .properties files from lang folder
     */
    private void loadAllTranslations() {
        translations.clear();

        if (!langFolder.exists()) {
            plugin.getLogger().warning("Lang folder not found, creating default...");
            langFolder.mkdirs();
            return;
        }

        File[] files =
                langFolder.listFiles((dir, name) -> name.endsWith(".properties") && !name.equals("styles.properties"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("No translation files found in lang folder");
            return;
        }

        for (File file : files) {
            String fileName = file.getName();
            String localeCode = fileName.replace(".properties", "");

            Locale locale = parseLocale(localeCode);
            if (locale == null) {
                plugin.getLogger().warning("Invalid locale file name: " + fileName);
                continue;
            }

            Properties properties = new Properties();
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                properties.load(reader);
                translations.put(locale, properties);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load translation file: " + fileName, e);
            }
        }
    }

    /**
     * Parses a locale code (e.g., "en", "de", "zh_TW", "zh_HK") to a Locale object
     */
    private Locale parseLocale(String localeCode) {
        if (localeCode == null || localeCode.isEmpty()) {
            return null;
        }

        String[] parts = localeCode.split("_");
        if (parts.length == 1) {
            return new Locale(parts[0]);
        } else if (parts.length == 2) {
            return new Locale(parts[0], parts[1]);
        } else if (parts.length == 3) {
            return new Locale(parts[0], parts[1], parts[2]);
        }

        return null;
    }

    /**
     * Gets the appropriate locale for a command sender
     */
    public Locale getLocale(CommandSender sender) {
        if (useClientLocale && sender instanceof Player player) {
            String clientLocale = player.getLocale();

            // Try exact match first (e.g., zh_TW)
            Locale locale = parseLocale(clientLocale);
            if (locale != null && translations.containsKey(locale)) {
                return locale;
            }

            // Try just the language part (e.g., "zh" from "zh_TW")
            if (clientLocale.contains("_")) {
                String language = clientLocale.split("_")[0];
                locale = new Locale(language);
                if (translations.containsKey(locale)) {
                    return locale;
                }
            }
        }

        return defaultLocale;
    }

    /**
     * Gets a translated message for a specific locale
     */
    public String getMessage(String key, Locale locale) {
        Properties props = translations.get(locale);
        if (props != null) {
            String message = props.getProperty(key);
            if (message != null) {
                return message;
            }
        }

        // Fallback to default locale
        if (!locale.equals(defaultLocale)) {
            props = translations.get(defaultLocale);
            if (props != null) {
                String message = props.getProperty(key);
                if (message != null) {
                    return message;
                }
            }
        }

        // Fallback to any available locale that has the key
        for (Properties p : translations.values()) {
            String message = p.getProperty(key);
            if (message != null) {
                return message;
            }
        }

        // Return key if not found
        plugin.getLogger().warning("Translation key not found: " + key + " for locale: " + locale);
        return "[Missing: " + key + "]";
    }

    /**
     * Gets a translated message for a command sender
     */
    public String getMessage(CommandSender sender, String key) {
        Locale locale = getLocale(sender);
        return getMessage(key, locale);
    }

    /**
     * Formats and sends a message to a command sender
     */
    public void sendMessage(CommandSender sender, String key, TagResolver... resolvers) {
        Locale locale = getLocale(sender);
        String message = getMessage(key, locale);

        Component component = formatter.format(message, resolvers);

        if (component.equals(Component.empty())) {
            return; // Don't send empty messages
        }

        Audience audience = adventure.sender(sender);
        audience.sendMessage(component);
    }

    /**
     * Formats a message without sending it
     */
    public Component formatMessage(CommandSender sender, String key, TagResolver... resolvers) {
        String message = getMessage(sender, key);
        return formatter.format(message, resolvers);
    }

    /**
     * Reloads all translations and styles
     */
    public void reload(Locale defaultLocale, boolean useClientLocale) {
        this.defaultLocale = defaultLocale;
        this.useClientLocale = useClientLocale;

        styleRegistry.reload();
        loadAllTranslations();
        formatter.clearCache();

        plugin.getLogger().info("Translations reloaded");
    }

    /**
     * Gets the style registry
     */
    public StyleRegistry getStyleRegistry() {
        return styleRegistry;
    }

    /**
     * Gets the message formatter
     */
    public MessageFormatter getFormatter() {
        return formatter;
    }

    /**
     * Gets the Adventure audiences instance
     */
    public BukkitAudiences getAdventure() {
        return adventure;
    }

    /**
     * Closes the translation manager and releases resources
     */
    public void close() {
        if (adventure != null) {
            adventure.close();
        }
        translations.clear();
        formatter.clearCache();
    }
}
