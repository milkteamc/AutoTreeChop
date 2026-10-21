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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyReleaseMigrationTest {
    @TempDir
    Path temp;

    @Test
    void both175ConfigLayoutsMigrateWithExactBackupAndNoSecondRewrite() throws Exception {
        for (String release : List.of("1.7.5-alpha", "1.7.5-release")) {
            byte[] original;
            try (InputStream input = getClass().getResourceAsStream("/config/legacy-" + release + ".yml")) {
                assertNotNull(input, release);
                original = input.readAllBytes();
            }
            Path folder = Files.createDirectory(temp.resolve(release));
            Path file = folder.resolve("config.yml");
            Files.write(file, original);
            YamlDocument source = ConfigSchema.parse(new String(original, java.nio.charset.StandardCharsets.UTF_8));
            try (InputStream defaults = getClass().getResourceAsStream("/config.yml")) {
                assertNotNull(defaults);
                ConfigSchema.prepare(file, defaults, ignored -> {}).commit();
            }
            Path backup;
            try (var files = Files.list(folder)) {
                List<Path> backups = files.filter(
                                p -> p.getFileName().toString().startsWith("config.yml.backup."))
                        .toList();
                assertEquals(1, backups.size(), release);
                backup = backups.get(0);
            }
            assertArrayEquals(original, Files.readAllBytes(backup), release);
            YamlDocument migrated = ConfigSchema.parse(Files.readString(file));
            assertEquals(4, migrated.getInt("config-version"), release);
            for (var entry : ConfigSchema.LEGACY_PATHS.entrySet()) {
                if (!source.contains(entry.getKey())) continue;
                if (source.isSection(entry.getKey())) {
                    assertEquals(
                            source.getSection(entry.getKey()).getStringRouteMappedValues(false),
                            migrated.getSection(entry.getValue()).getStringRouteMappedValues(false),
                            release + " " + entry.getKey());
                } else {
                    assertEquals(
                            source.get(entry.getKey()), migrated.get(entry.getValue()), release + " " + entry.getKey());
                }
                assertFalse(migrated.contains(entry.getKey()), release + " " + entry.getKey());
            }
            byte[] firstMigration = Files.readAllBytes(file);
            try (InputStream defaults = getClass().getResourceAsStream("/config.yml")) {
                ConfigSchema.prepare(file, defaults, ignored -> {}).commit();
            }
            assertTrue(Arrays.equals(firstMigration, Files.readAllBytes(file)), release);
            try (var files = Files.list(folder)) {
                assertEquals(
                        1,
                        files.filter(p -> p.getFileName().toString().startsWith("config.yml.backup."))
                                .count(),
                        release);
            }
        }
    }
}
