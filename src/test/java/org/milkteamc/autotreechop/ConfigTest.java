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
 
package org.milkteamc.autotreechop;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.dejvokep.boostedyaml.YamlDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.milkteamc.autotreechop.configuration.ConfigLoadException;
import org.milkteamc.autotreechop.configuration.ConfigSchema;

class ConfigTest {
    @TempDir
    Path temp;

    private AutoTreeChop plugin;

    @BeforeEach
    void setup() {
        plugin = mock(AutoTreeChop.class);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getResource("config.yml")).thenAnswer(call -> getClass().getResourceAsStream("/config.yml"));
    }

    private Path file() {
        return temp.resolve("config.yml");
    }

    @Test
    void structureConfirmationDefaultsOnAndCanBeSafelyReloaded() throws Exception {
        Files.writeString(file(), "config-version: 4\nsafety:\n  no-leaves-confirmation: false\n");
        Config config = new Config(plugin);
        assertTrue(config.isPlayerStructureConfirmationEnabled());
        assertFalse(config.isNoLeavesConfirmationEnabled());
        assertTrue(Files.readString(file()).contains("player-structure-confirmation: true"));
        Files.writeString(file(), "config-version: 4\nsafety:\n  player-structure-confirmation: false\n");
        config.load();
        assertFalse(config.isPlayerStructureConfirmationEnabled());
        Files.writeString(file(), "config-version: 4\nsafety:\n  player-structure-confirmation: maybe\n");
        assertThrows(ConfigLoadException.class, config::load);
        assertFalse(config.isPlayerStructureConfirmationEnabled());
    }

    @Test
    void invalidReloadKeepsAllOldSettingsAndTheEditedFile() throws Exception {
        Config config = new Config(plugin);
        Set<Material> logs = config.getLogTypes();
        String invalid = """
                config-version: 4
                chopping:
                  visual-effect: false
                  log-types: [DIAMOND_ORE]
                groups:
                  default:
                    max-uses-per-day: 7
                leaves:
                  batch-size: 0
                """;
        Files.writeString(file(), invalid);
        assertThrows(ConfigLoadException.class, config::load);
        assertTrue(config.isVisualEffect());
        assertEquals(50, config.getMaxUsesPerDay());
        assertSame(logs, config.getLogTypes());
        assertEquals(invalid, Files.readString(file()));
    }

    @Test
    void validReloadPublishesImmutableMaterialCollections() throws Exception {
        Config config = new Config(plugin);
        Files.writeString(file(), """
                config-version: 4
                messages:
                  locale: zh_TW
                chopping:
                  visual-effect: false
                  log-types: [DIAMOND_ORE]
                  root-types: []
                replant:
                  log-sapling-mapping: {}
                """);
        config.load();
        assertFalse(config.isVisualEffect());
        assertEquals(Locale.TAIWAN, config.getLocale());
        assertEquals(Set.of(Material.DIAMOND_ORE), config.getLogTypes());
        assertThrows(
                UnsupportedOperationException.class, () -> config.getLogTypes().add(Material.OAK_LOG));
        assertThrows(
                UnsupportedOperationException.class, () -> config.getLeafTypes().clear());
        assertThrows(UnsupportedOperationException.class, () -> config.getLogSaplingMapping()
                .put(Material.OAK_LOG, Material.OAK_SAPLING));
    }

    @Test
    void restartOnlyValuesRemainEffectiveUntilNextStartup() throws Exception {
        Config config = new Config(plugin);
        YamlDocument edited = ConfigSchema.parse(Files.readString(file()));
        edited.set("storage.use-mysql", true);
        edited.set("storage.mysql.password", "new-secret");
        edited.set("storage.mysql.port", 3307);
        edited.set("integrations.residence.flag", "destroy");
        edited.set("chopping.batch-size", 6);
        Files.writeString(file(), edited.dump());
        config.load();
        assertFalse(config.isUseMysql());
        assertEquals("abc1234", config.getPassword());
        assertEquals(3306, config.getPort());
        assertEquals("build", config.getResidenceFlag());
        assertEquals(6, config.getChopBatchSize());
        assertEquals(
                List.of(
                        "storage.use-mysql",
                        "storage.mysql.port",
                        "storage.mysql.password",
                        "integrations.residence.flag"),
                config.getRestartRequiredSettings());
        assertEquals("new-secret", ConfigSchema.parse(Files.readString(file())).getString("storage.mysql.password"));
        config.load();
        assertFalse(config.isUseMysql(), "Reloading again must not apply settings requiring restart");
        Config restarted = new Config(plugin);
        assertTrue(restarted.isUseMysql());
        assertEquals("new-secret", restarted.getPassword());
        assertEquals(3307, restarted.getPort());
        assertEquals("destroy", restarted.getResidenceFlag());
        assertTrue(restarted.getRestartRequiredSettings().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void migrationConvertsOldToggleCombinationsAndPreservesUsageFlags(int mask) throws Exception {
        boolean command = (mask & 1) != 0;
        boolean sneak = (mask & 2) != 0;
        Files.writeString(
                file(),
                "config-version: 3\nenable-command-toggle: " + command + "\nenable-sneak-toggle: " + sneak
                        + "\nlimitUsage: false\nlimitVipUsage: true\n");
        Config config = new Config(plugin);
        String expected = command ? (sneak ? "command-and-sneak" : "command") : (sneak ? "sneak" : "disabled");
        var migrated = ConfigSchema.parse(Files.readString(file()));
        assertEquals(expected, migrated.getString("activation.mode"));
        assertEquals(command, config.isCommandActivationEnabled());
        assertFalse(migrated.contains("enable-command-toggle"));
        assertFalse(migrated.contains("enable-sneak-toggle"));
        assertFalse(migrated.contains("activation.command-toggle"));
        assertFalse(migrated.contains("activation.sneak-toggle"));
        assertFalse(config.getLimitUsage());
        assertTrue(config.getLimitVipUsage());
    }

    @Test
    void failedBackupDoesNotPublishOrReplaceSettings() throws Exception {
        Config config = new Config(plugin);
        String edited = "config-version: 3\nmax-uses-per-day: 9\n";
        Files.writeString(file(), edited);
        try (var fileSystem = mockStatic(Files.class, call -> {
            if (call.getMethod().getName().equals("createTempFile")
                    && call.getArgument(1).equals("config.yml.backup.")) {
                throw new java.io.IOException("Backup unavailable");
            }
            return call.callRealMethod();
        })) {
            assertThrows(ConfigLoadException.class, config::load);
        }
        assertEquals(50, config.getMaxUsesPerDay());
        assertEquals(edited, Files.readString(file()));
    }

    @Test
    void removesAnIncompleteBackupWhenWritingItFails() throws Exception {
        Config config = new Config(plugin);
        String edited = "config-version: 3\nmax-uses-per-day: 9\n";
        Files.writeString(file(), edited);
        try (var fileSystem = mockStatic(Files.class, call -> {
            if (call.getMethod().getName().equals("write")
                    && ((Path) call.getArgument(0)).getFileName().toString().startsWith("config.yml.backup.")) {
                try (var partial = new java.io.FileOutputStream(((Path) call.getArgument(0)).toFile())) {
                    partial.write(1);
                }
                throw new java.io.IOException("Backup write interrupted");
            }
            return call.callRealMethod();
        })) {
            assertThrows(ConfigLoadException.class, config::load);
        }
        assertEquals(50, config.getMaxUsesPerDay());
        assertEquals(edited, Files.readString(file()));
        try (var files = Files.list(temp)) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith("config.yml.backup.")));
        }
    }

    @Test
    void failedAtomicReplacementPreservesTheOriginalAndActiveSettings() throws Exception {
        Config config = new Config(plugin);
        String edited = "config-version: 3\nmax-uses-per-day: 9\n";
        Files.writeString(file(), edited);
        try (var fileSystem = mockStatic(Files.class, call -> {
            if (call.getMethod().getName().equals("move")) {
                throw new java.nio.file.AtomicMoveNotSupportedException("temporary", "config.yml", "Not supported");
            }
            return call.callRealMethod();
        })) {
            assertThrows(ConfigLoadException.class, config::load);
        }
        assertEquals(50, config.getMaxUsesPerDay());
        assertEquals(edited, Files.readString(file()));
        try (var files = Files.list(temp)) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith(".config-")));
        }
        try (var files = Files.list(temp)) {
            Path backup = files.filter(p -> p.getFileName().toString().startsWith("config.yml.backup."))
                    .findFirst()
                    .orElseThrow();
            assertEquals(edited, Files.readString(backup));
        }
    }

    @Test
    void invalidInitialConfigIsNotReplacedByDefaults() throws Exception {
        Files.writeString(file(), "chopping: invalid");
        assertThrows(ConfigLoadException.class, () -> new Config(plugin));
        assertEquals("chopping: invalid", Files.readString(file()));
    }
}
