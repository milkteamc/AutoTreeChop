/*
 * Copyright (C) 2026 MilkTeaMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
 
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

        // Update translation files with missing keys
        updateTranslationFiles();

        // Load all language files
        loadAllTranslations();
    }

    private void updateTranslationFiles() {
        if (!langFolder.exists()) {
            return;
        }

        File[] files =
                langFolder.listFiles((dir, name) -> name.endsWith(".properties") && !name.equals("styles.properties"));

        if (files == null || files.length == 0) {
            return;
        }

        for (File userFile : files) {
            String fileName = userFile.getName();

            try (InputStream defaultStream = plugin.getResource("lang/" + fileName)) {
                if (defaultStream == null) {
                    continue;
                }

                Properties defaultProps = new Properties();
                try (InputStreamReader reader = new InputStreamReader(defaultStream, StandardCharsets.UTF_8)) {
                    defaultProps.load(reader);
                }

                Properties userProps = new Properties();
                try (InputStreamReader reader =
                        new InputStreamReader(new FileInputStream(userFile), StandardCharsets.UTF_8)) {
                    userProps.load(reader);
                }

                // Use TreeSet so missing keys are appended in alphabetical order,
                // making the generated file consistent across server restarts.
                Set<String> missingKeys = new TreeSet<>(defaultProps.stringPropertyNames());
                missingKeys.removeAll(userProps.stringPropertyNames());

                if (!missingKeys.isEmpty()) {
                    boolean needsLeadingNewline = false;
                    if (userFile.length() > 0) {
                        try (RandomAccessFile raf = new RandomAccessFile(userFile, "r")) {
                            raf.seek(userFile.length() - 1);
                            needsLeadingNewline = raf.readByte() != '\n';
                        }
                    }

                    try (OutputStreamWriter writer =
                            new OutputStreamWriter(new FileOutputStream(userFile, true), StandardCharsets.UTF_8)) {

                        if (needsLeadingNewline) {
                            writer.write("\n");
                        }

                        for (String key : missingKeys) {
                            String value = defaultProps.getProperty(key);
                            String escapedValue = escapePropertyValue(value);
                            writer.write(key + "=" + escapedValue + "\n");
                        }

                        writer.flush();
                    }
                }

            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to update translation file: " + fileName, e);
            }
        }
    }

    /**
     * Escapes a property value for safe writing to a {@code .properties} file.
     *
     * <p>Handles all characters that {@link Properties#load} treats specially:
     * <ul>
     *   <li>{@code \} → {@code \\}</li>
     *   <li>newline / carriage-return / tab → {@code \n} / {@code \r} / {@code \t}</li>
     *   <li>Leading spaces and form-feeds — prefixed with {@code \} so that
     *       {@code Properties.load()} does not strip them as whitespace before
     *       the value.</li>
     * </ul>
     *
     * <p>Note: {@code #} and {@code !} do <em>not</em> need escaping when they appear
     * in a value written as {@code key=value}, because {@code Properties.load()} only
     * treats them as comment starters at the very beginning of a line (i.e. as a key).
     */
    private String escapePropertyValue(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(value.length());
        boolean leadingWhitespace = true;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    leadingWhitespace = false;
                    break;
                case '\n':
                    sb.append("\\n");
                    leadingWhitespace = false;
                    break;
                case '\r':
                    sb.append("\\r");
                    leadingWhitespace = false;
                    break;
                case '\t':
                    // Tabs are always escaped regardless of position so the round-trip
                    // is lossless (Properties.load strips leading whitespace including tabs).
                    sb.append("\\t");
                    // A tab does not end the leading-whitespace region for spaces,
                    // but since we escape it unconditionally the flag is moot here.
                    break;
                case ' ':
                case '\f':
                    if (leadingWhitespace) {
                        // Prefix with backslash so Properties.load() preserves the space.
                        sb.append('\\').append(c);
                    } else {
                        sb.append(c);
                    }
                    break;
                default:
                    sb.append(c);
                    leadingWhitespace = false;
                    break;
            }
        }

        return sb.toString();
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
        String languageTag = localeCode.replace('_', '-');
        return Locale.forLanguageTag(languageTag);
    }

    /**
     * Gets the appropriate locale for a command sender
     */
    public Locale getLocale(CommandSender sender) {
        if (useClientLocale && sender instanceof Player player) {
            Locale clientLocale = player.locale();

            if (translations.containsKey(clientLocale)) {
                return clientLocale;
            }

            Locale languageOnly = Locale.forLanguageTag(clientLocale.getLanguage());
            if (translations.containsKey(languageOnly)) {
                return languageOnly;
            }
        }

        return defaultLocale;
    }

    /**
     * Gets a translated message for a specific locale.
     *
     * <p>Fallback priority:
     * <ol>
     *   <li>Requested locale</li>
     *   <li>English ({@link Locale#ENGLISH}) — always the canonical reference translation</li>
     *   <li>The configured default locale (if different from English)</li>
     *   <li>Any loaded locale that contains the key</li>
     * </ol>
     */
    public String getMessage(String key, Locale locale) {
        // 1. Requested locale
        Properties props = translations.get(locale);
        if (props != null) {
            String message = props.getProperty(key);
            if (message != null) {
                return message;
            }
        }

        // 2. English — highest-priority fallback because en.properties is always
        //    kept complete and is the canonical source for new keys.
        Locale english = Locale.ENGLISH;
        if (!locale.equals(english)) {
            props = translations.get(english);
            if (props != null) {
                String message = props.getProperty(key);
                if (message != null) {
                    return message;
                }
            }
        }

        // 3. Configured default locale (skip if it is English — already tried above)
        if (!locale.equals(defaultLocale) && !defaultLocale.equals(english)) {
            props = translations.get(defaultLocale);
            if (props != null) {
                String message = props.getProperty(key);
                if (message != null) {
                    return message;
                }
            }
        }

        // 4. Last resort: any loaded locale that has the key
        for (Properties p : translations.values()) {
            String message = p.getProperty(key);
            if (message != null) {
                return message;
            }
        }

        // Key genuinely missing from every loaded file
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
        updateTranslationFiles();
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
