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

import static org.junit.jupiter.api.Assertions.*;

import dev.dejvokep.boostedyaml.YamlDocument;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigSchemaTest {
    @TempDir
    Path temp;

    private final List<String> warnings = new ArrayList<>();

    private ConfigSchema.Prepared prepare() throws Exception {
        try (InputStream defaults = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(defaults);
            return ConfigSchema.prepare(temp.resolve("config.yml"), defaults, warnings::add);
        }
    }

    private String legacy() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/config/legacy-v3.yml")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<Path> backups() throws Exception {
        try (var files = Files.list(temp)) {
            return files.filter(p -> p.getFileName().toString().startsWith("config.yml.backup."))
                    .toList();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void migratesOldVersionsWithExactBackupAndPreservesCustomValues(int version) throws Exception {
        String contents = legacy().replace("config-version: 3", "config-version: " + version)
                        .replace("max-uses-per-day: 50", "max-uses-per-day: 17")
                        .replace("password: abc1234", "password: 'secret: <tag>'")
                        .replace("locale: en", "locale: zh_TW")
                + "\ncustom-setting: keep-me\n";
        YamlDocument customized = ConfigSchema.parse(contents);
        customized.set("log-types", List.of("DIAMOND_ORE"));
        customized.set("root-types", List.of());
        customized.set("leaf-types", List.of());
        customized.set("additional-leaf-types", List.of());
        customized.set("log-sapling-mapping", java.util.Map.of("DIAMOND_ORE", "OAK_SAPLING"));
        contents = customized.dump();
        Path file = temp.resolve("config.yml");
        Files.writeString(file, contents);
        ConfigSchema.Prepared prepared = prepare();
        assertEquals(contents, Files.readString(file), "Preparing must not mutate the file");
        assertTrue(backups().isEmpty());
        prepared.commit();
        assertEquals(contents, Files.readString(backups().get(0)));
        YamlDocument result = ConfigSchema.parse(Files.readString(file));
        assertEquals(4, result.getInt("config-version"));
        assertEquals(17, result.getInt("groups.default.max-uses-per-day"));
        assertEquals("secret: <tag>", result.getString("storage.mysql.password"));
        assertEquals("zh_TW", result.getString("messages.locale"));
        assertEquals(List.of("DIAMOND_ORE"), result.getStringList("chopping.log-types"));
        assertEquals(List.of(), result.getStringList("chopping.root-types"));
        assertEquals(List.of(), result.getStringList("leaves.types"));
        assertEquals(List.of(), result.getStringList("leaves.additional-types"));
        assertEquals(
                java.util.Set.of("DIAMOND_ORE"),
                result.getSection("replant.log-sapling-mapping").getKeys());
        assertEquals("keep-me", result.getString("custom-setting"));
        assertTrue(warnings.stream().anyMatch(s -> s.contains("custom-setting")));
        assertTrue(ConfigSchema.LEGACY_PATHS.keySet().stream().noneMatch(result::contains));
        String migrated = Files.readString(file);
        prepared.commit();
        prepare().commit();
        assertEquals(migrated, Files.readString(file));
        assertEquals(1, backups().size(), "Repeated reloads must not create new backups or rewrite the file");
    }

    @Test
    void migratesEveryLegacyKeyWithoutChangingItsValue() throws Exception {
        YamlDocument original = ConfigSchema.parse(legacy());
        assertEquals(original.getKeys().size() - 3, ConfigSchema.LEGACY_PATHS.size());
        Files.writeString(temp.resolve("config.yml"), legacy());
        YamlDocument migrated = prepare().document();
        for (var entry : ConfigSchema.LEGACY_PATHS.entrySet()) {
            if (entry.getKey().equals("log-sapling-mapping")) {
                assertEquals(
                        original.getSection(entry.getKey()).getStringRouteMappedValues(false),
                        migrated.getSection(entry.getValue()).getStringRouteMappedValues(false));
            } else {
                assertEquals(original.get(entry.getKey()), migrated.get(entry.getValue()), entry.getKey());
            }
        }
    }

    @Test
    void explicitNewKeysWinIncludingFalseZeroAndEmptyCollections() throws Exception {
        Files.writeString(temp.resolve("config.yml"), """
                config-version: 3
                enable-command-toggle: true
                max-uses-per-day: 50
                root-types: [MANGROVE_ROOTS]
                log-sapling-mapping: {OAK_LOG: OAK_SAPLING}
                activation:
                  command-toggle: false
                groups:
                  default:
                    max-uses-per-day: 0
                chopping:
                  root-types: []
                replant:
                  log-sapling-mapping: {}
                """);
        YamlDocument result = prepare().document();
        assertEquals("disabled", result.getString("activation.mode"));
        assertFalse(result.contains("activation.command-toggle"));
        assertEquals(0, result.getInt("groups.default.max-uses-per-day"));
        assertTrue(result.getStringList("chopping.root-types").isEmpty());
        assertTrue(result.getSection("replant.log-sapling-mapping").getKeys().isEmpty());
        assertEquals(4, warnings.size());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "config-version: 5",
                "config-version: 0",
                "config-version: nope",
                "storage: broken",
                "storage: {mysql: false}",
                "storage: {mysql: {port: 65536}}",
                "storage: {mysql: {password: 123}}",
                "activation: {command-toggle: null}",
                "activation: {sneak-toggle: 'false'}",
                "leaves: {radius: -1}",
                "leaves: {batch-size: 0}",
                "leaves: {mode: everything}",
                "chopping: {batch-size: 1.5}",
                "chopping: {max-tree-size: 2147483648}",
                "chopping: {log-types: [OAK_LOG, 23]}",
                "replant: {log-sapling-mapping: {OAK_LOG: []}}",
                "messages: {locale: 'bad!'}",
                "config-version: 3\nconfig-version: 4",
                "password: [broken"
            })
    void rejectsInvalidDocumentsWithoutWritingOrBackingUp(String contents) throws Exception {
        Path file = temp.resolve("config.yml");
        Files.writeString(file, contents);
        assertThrows(ConfigLoadException.class, this::prepare);
        assertEquals(contents, Files.readString(file));
        assertTrue(backups().isEmpty());
    }

    @Test
    void yamlErrorsDoNotExposeSecrets() throws Exception {
        Files.writeString(temp.resolve("config.yml"), "password: [never-print-this-secret");
        ConfigLoadException failure = assertThrows(ConfigLoadException.class, this::prepare);
        assertFalse(failure.toString().contains("never-print-this-secret"));
        assertNull(failure.getCause());
    }

    @Test
    void backsUpOriginalBytesBeforeSanitizingBomAndControls() throws Exception {
        byte[] original = ("\uFEFFconfig-version: 3\nlocale: en\u0000\n").getBytes(StandardCharsets.UTF_8);
        Files.write(temp.resolve("config.yml"), original);
        prepare().commit();
        assertArrayEquals(original, Files.readAllBytes(backups().get(0)));
        String written = Files.readString(temp.resolve("config.yml"));
        assertFalse(written.contains("\uFEFF"));
        assertFalse(written.contains("\u0000"));
    }

    @Test
    void rejectsMalformedUtf8WithoutChangingTheFile() throws Exception {
        byte[] invalid = {(byte) 0xC3, (byte) 0x28};
        Files.write(temp.resolve("config.yml"), invalid);
        assertThrows(ConfigLoadException.class, this::prepare);
        assertArrayEquals(invalid, Files.readAllBytes(temp.resolve("config.yml")));
    }

    @Test
    void detectsConcurrentFileChangesBeforeCommitting() throws Exception {
        Path file = temp.resolve("config.yml");
        Files.writeString(file, "config-version: 3\n");
        var prepared = prepare();
        Files.writeString(file, "# an administrator edited this\nconfig-version: 3\n");
        assertThrows(ConfigLoadException.class, prepared::commit);
        assertTrue(Files.readString(file).contains("administrator"));
        assertTrue(backups().isEmpty());
    }

    @Test
    void newInstallationUsesBundledDefaultsWithoutABackup() throws Exception {
        var prepared = prepare();
        prepared.commit();
        assertTrue(Files.exists(temp.resolve("config.yml")));
        assertTrue(backups().isEmpty());
        assertTrue(prepared.document().getBoolean("messages.use-player-locale"));
    }
}
