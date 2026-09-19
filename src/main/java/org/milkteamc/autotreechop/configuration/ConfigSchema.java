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
 
package org.milkteamc.autotreechop.configuration;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class ConfigSchema {
    public static final int VERSION = 4;
    public static final Map<String, String> LEGACY_PATHS = legacyPaths();
    private static final String MAPPING_PATH = "replant.log-sapling-mapping";
    private static final Set<String> GROUP_OPTIONS =
            Set.of("priority", "limit-usage", "max-uses-per-day", "max-blocks-per-day", "cooldown-seconds");
    private static final Set<String> POSITIVE = Set.of(
            "chopping.batch-size",
            "chopping.max-tree-size",
            "chopping.max-discovery-blocks",
            "leaves.batch-size",
            "safety.confirmation-window-seconds");
    private static final Set<String> LONG_VALUES = Set.of("leaves.delay-ticks", "replant.delay-ticks");

    private ConfigSchema() {}

    private static Map<String, String> legacyPaths() {
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("locale", "messages.locale");
        paths.put("use-player-locale", "messages.use-player-locale");
        paths.put("limitUsage", "chopping.limit-usage");
        paths.put("max-uses-per-day", "groups.default.max-uses-per-day");
        paths.put("max-blocks-per-day", "groups.default.max-blocks-per-day");
        paths.put("cooldownTime", "groups.default.cooldown-seconds");
        paths.put("limitVipUsage", "groups.vip.limit-usage");
        paths.put("vip-uses-per-day", "groups.vip.max-uses-per-day");
        paths.put("vip-blocks-per-day", "groups.vip.max-blocks-per-day");
        paths.put("vipCooldownTime", "groups.vip.cooldown-seconds");
        paths.put("useMysql", "storage.use-mysql");
        paths.put("hostname", "storage.mysql.hostname");
        paths.put("port", "storage.mysql.port");
        paths.put("database", "storage.mysql.database");
        paths.put("username", "storage.mysql.username");
        paths.put("password", "storage.mysql.password");
        paths.put("visual-effect", "chopping.visual-effect");
        paths.put("toolDamage", "chopping.tool-damage.enabled");
        paths.put("toolDamageDecrease", "chopping.tool-damage.amount");
        paths.put("idle-timeout", "safety.idle-timeout-seconds");
        paths.put("confirmation-window", "safety.confirmation-window-seconds");
        paths.put("enable-no-leaves-confirmation", "safety.no-leaves-confirmation");
        paths.put("prevent-no-leaves-chopping", "safety.prevent-no-leaves-chopping");
        paths.put("enable-idle-confirmation", "safety.idle-confirmation");
        paths.put("no-leaves-detection-radius", "safety.no-leaves-detection-radius");
        paths.put("mustUseTool", "chopping.require-tool");
        paths.put("defaultTreeChop", "activation.default-enabled");
        paths.put("respectUnbreaking", "chopping.tool-damage.respect-unbreaking");
        paths.put("playBreakSound", "chopping.play-break-sound");
        paths.put("enable-command-toggle", "activation.command-toggle");
        paths.put("enable-sneak-toggle", "activation.sneak-toggle");
        paths.put("sneak-message", "activation.sneak-message");
        paths.put("chop-batch-size", "chopping.batch-size");
        paths.put("max-tree-size", "chopping.max-tree-size");
        paths.put("max-discovery-blocks", "chopping.max-discovery-blocks");
        paths.put("call-block-break-event", "integrations.call-block-break-event");
        paths.put("residenceFlag", "integrations.residence.flag");
        paths.put("griefPreventionFlag", "integrations.grief-prevention.flag");
        paths.put("stopChoppingIfNotConnected", "chopping.stop-if-not-connected");
        paths.put("stopChoppingIfDifferentTypes", "chopping.stop-if-different-types");
        paths.put("log-types", "chopping.log-types");
        paths.put("root-types", "chopping.root-types");
        paths.put("enable-leaf-removal", "leaves.enabled");
        paths.put("leaf-removal-mode", "leaves.mode");
        paths.put("leaf-removal-delay-ticks", "leaves.delay-ticks");
        paths.put("leaf-removal-radius", "leaves.radius");
        paths.put("leaf-removal-drop-items", "leaves.drop-items");
        paths.put("leaf-removal-visual-effects", "leaves.visual-effects");
        paths.put("leaf-removal-async", "leaves.async");
        paths.put("leaf-removal-batch-size", "leaves.batch-size");
        paths.put("leaf-removal-counts-towards-limit", "leaves.counts-towards-limit");
        paths.put("leaf-types", "leaves.types");
        paths.put("additional-leaf-types", "leaves.additional-types");
        paths.put("enable-auto-replant", "replant.enabled");
        paths.put("replant-delay-ticks", "replant.delay-ticks");
        paths.put("require-sapling-in-inventory", "replant.require-sapling-in-inventory");
        paths.put("replant-visual-effect", "replant.visual-effect");
        paths.put("log-sapling-mapping", "replant.log-sapling-mapping");
        paths.put("valid-soil-types", "replant.valid-soil-types");
        return Collections.unmodifiableMap(paths);
    }

    public static YamlDocument parse(String contents) throws IOException {
        try (InputStream input = new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))) {
            return YamlDocument.create(
                    input,
                    LoaderSettings.builder()
                            .setAutoUpdate(false)
                            .setCreateFileIfAbsent(false)
                            .setAllowDuplicateKeys(false)
                            .setDetailedErrors(false)
                            .build());
        } catch (RuntimeException e) {
            // Parser exceptions may contain database passwords from the source line.
            throw new ConfigLoadException("config.yml contains invalid YAML; check indentation, keys and value syntax");
        }
    }

    public static Prepared prepare(Path file, InputStream defaultsStream, Consumer<String> warning) throws IOException {
        String bundled = new String(defaultsStream.readAllBytes(), StandardCharsets.UTF_8);
        YamlDocument defaults = parse(bundled);
        byte[] original = Files.exists(file) ? Files.readAllBytes(file) : null;
        String contents = original == null ? bundled : decode(original);
        String sanitized = contents.startsWith("\uFEFF") ? contents.substring(1) : contents;
        sanitized = sanitized.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        YamlDocument document = parse(sanitized);
        int version = document.contains("config-version")
                ? (int) integer(document.get("config-version"), "config-version", 1, VERSION)
                : 1;
        boolean changed = original == null || !contents.equals(sanitized) || version != VERSION;

        for (var entry : LEGACY_PATHS.entrySet()) {
            String oldPath = entry.getKey();
            String newPath = entry.getValue();
            requireSections(document, newPath);
            if (document.contains(oldPath)) {
                if (document.contains(newPath)) {
                    warning.accept("Both " + oldPath + " and " + newPath + " are set; using " + newPath);
                    document.remove(oldPath);
                } else {
                    document.move(oldPath, newPath);
                }
                changed = true;
            }
            if (!document.contains(newPath)) {
                document.set(newPath, rawValue(defaults.get(newPath)));
                changed = true;
            }
            validate(document.get(newPath), defaults.get(newPath), newPath);
        }
        validateGroups(document);
        document.set("config-version", VERSION);
        for (String path : document.getRoutesAsStrings(true)) {
            if (path.equals("config-version")
                    || LEGACY_PATHS.containsValue(path)
                    || path.startsWith(MAPPING_PATH + ".")
                    || isGroupPath(path)
                    || LEGACY_PATHS.values().stream().anyMatch(known -> known.startsWith(path + "."))) continue;
            warning.accept("Unrecognized config key retained: " + path);
        }
        return new Prepared(file, original, document, changed);
    }

    private static void validateGroups(YamlDocument document) {
        Section groups = document.getSection("groups");
        for (Object key : groups.getKeys()) {
            if (!(key instanceof String name) || !name.matches("[a-z0-9_-]+")) {
                throw invalid("groups", "group names must use lowercase letters, digits, underscores or hyphens");
            }
            String path = "groups." + name;
            if (!document.isSection(path)) throw invalid(path, "must be a section");
            Section group = document.getSection(path);
            for (String option : GROUP_OPTIONS) {
                if (group.contains(option)) {
                    validate(
                            group.get(option),
                            option.equals("limit-usage") ? Boolean.TRUE : Integer.valueOf(0),
                            path + "." + option);
                }
            }
        }
    }

    private static boolean isGroupPath(String path) {
        String[] parts = path.split("\\.");
        return parts[0].equals("groups")
                && (parts.length == 2 || (parts.length == 3 && GROUP_OPTIONS.contains(parts[2])));
    }

    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new ConfigLoadException("config.yml must be valid UTF-8");
        }
    }

    private static Object rawValue(Object value) {
        if (value instanceof Section section) {
            Map<Object, Object> values = new LinkedHashMap<>();
            section.getKeys().forEach(key -> values.put(key, rawValue(section.get(key.toString()))));
            return values;
        }
        return value;
    }

    private static void requireSections(YamlDocument document, String path) {
        int index = path.indexOf('.');
        while (index >= 0) {
            String parent = path.substring(0, index);
            if (document.contains(parent) && !document.isSection(parent)) {
                throw invalid(parent, "must be a section");
            }
            index = path.indexOf('.', index + 1);
        }
    }

    private static void validate(Object value, Object expected, String path) {
        if (expected instanceof Boolean) {
            if (!(value instanceof Boolean)) throw invalid(path, "must be true or false");
        } else if (expected instanceof Number) {
            long minimum = POSITIVE.contains(path) || path.equals("storage.mysql.port") ? 1 : 0;
            long maximum = path.equals("storage.mysql.port")
                    ? 65535
                    : LONG_VALUES.contains(path) ? Long.MAX_VALUE : Integer.MAX_VALUE;
            integer(value, path, minimum, maximum);
        } else if (expected instanceof String) {
            if (!(value instanceof String text)) throw invalid(path, "must be a string");
            if (!path.equals("storage.mysql.password") && text.isBlank()) throw invalid(path, "must not be empty");
            if (path.equals("leaves.mode")
                    && !Set.of("smart", "radius", "aggressive").contains(text)) {
                throw invalid(path, "must be smart, radius or aggressive");
            }
            if (path.equals("messages.locale")) {
                try {
                    Locale locale = new Locale.Builder()
                            .setLanguageTag(text.replace('_', '-'))
                            .build();
                    if (locale.getLanguage().isEmpty()) throw invalid(path, "must be a language tag");
                } catch (java.util.IllformedLocaleException e) {
                    throw invalid(path, "must be a language tag such as en or zh-TW");
                }
            }
        } else if (expected instanceof List<?>) {
            if (!(value instanceof List<?> list)
                    || list.stream().anyMatch(item -> !(item instanceof String s) || s.isBlank())) {
                throw invalid(path, "must be a list of material names (or [])");
            }
        } else if (expected instanceof Section) {
            if (!(value instanceof Section section)) throw invalid(path, "must be a material mapping (or {})");
            for (Object key : section.getKeys()) {
                Object entry = section.get(key.toString());
                if (!(key instanceof String name)
                        || name.isBlank()
                        || name.contains(".")
                        || !(entry instanceof String target)
                        || target.isBlank()) {
                    throw invalid(path, "must map material names to material names");
                }
            }
        } else {
            throw invalid(path, "has no bundled schema definition");
        }
    }

    private static long integer(Object value, String path, long minimum, long maximum) {
        if (!(value instanceof Number number)) throw invalid(path, "must be an integer");
        try {
            long result = new BigDecimal(number.toString()).longValueExact();
            if (result < minimum || result > maximum)
                throw invalid(path, "must be between " + minimum + " and " + maximum);
            return result;
        } catch (ArithmeticException | NumberFormatException e) {
            throw invalid(path, "must be an integer within range");
        }
    }

    private static ConfigLoadException invalid(String path, String reason) {
        return new ConfigLoadException("Invalid config key " + path + ": " + reason);
    }

    public static final class Prepared {
        private final Path file;
        private final byte[] original;
        private final YamlDocument document;
        private final boolean changed;
        private boolean committed;

        private Prepared(Path file, byte[] original, YamlDocument document, boolean changed) {
            this.file = file;
            this.original = original;
            this.document = document;
            this.changed = changed;
        }

        public YamlDocument document() {
            return document;
        }

        public void commit() throws IOException {
            if (committed || !changed) return;
            byte[] current = Files.exists(file) ? Files.readAllBytes(file) : null;
            if (!Arrays.equals(original, current)) {
                throw new ConfigLoadException("config.yml changed while loading; retry the reload");
            }
            Path directory = file.toAbsolutePath().getParent();
            Files.createDirectories(directory);
            if (original != null) {
                Path backup = Files.createTempFile(directory, "config.yml.backup.", "");
                try {
                    Files.write(backup, original);
                } catch (IOException failure) {
                    Files.deleteIfExists(backup);
                    throw failure;
                }
            }
            Path temporary = Files.createTempFile(directory, ".config-", ".yml");
            try {
                Files.writeString(temporary, document.dump(), StandardCharsets.UTF_8);
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                committed = true;
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
