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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundledLanguagesTest {
    @TempDir
    Path temp;

    private Path jar(String... names) throws Exception {
        Path jar = temp.resolve("plugin.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String name : names) {
                out.putNextEntry(new JarEntry(name));
                out.write("message=hello".getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return jar;
    }

    @Test
    void discoversNewLanguagesAndStylesWithoutDirectoryEntries() throws Exception {
        Path jar = jar(
                "lang/en.properties",
                "lang/it.properties",
                "lang/tr.properties",
                "lang/zh_CN.properties",
                "lang/pt-BR.properties",
                "lang/styles.properties");
        Path languages = temp.resolve("lang");
        BundledLanguages.copyMissing(jar, languages);
        try (var files = Files.list(languages)) {
            assertEquals(
                    List.of(
                            "en.properties",
                            "it.properties",
                            "pt-BR.properties",
                            "styles.properties",
                            "tr.properties",
                            "zh_CN.properties"),
                    files.map(p -> p.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    void preservesCustomFilesAndRestoresMissingBundledFiles() throws Exception {
        Path jar = jar("lang/en.properties", "lang/styles.properties");
        Path languages = temp.resolve("lang");
        BundledLanguages.copyMissing(jar, languages);
        Files.writeString(languages.resolve("en.properties"), "message=custom");
        Files.writeString(languages.resolve("ko.properties"), "message=custom Korean");
        Files.delete(languages.resolve("styles.properties"));
        BundledLanguages.copyMissing(jar, languages);
        assertEquals("message=custom", Files.readString(languages.resolve("en.properties")));
        assertEquals("message=custom Korean", Files.readString(languages.resolve("ko.properties")));
        assertTrue(Files.exists(languages.resolve("styles.properties")));
    }

    @Test
    void ignoresNestedTraversalAndNonPropertiesResources() throws Exception {
        Path jar = jar(
                "lang/../escape.properties",
                "lang/nested/fr.properties",
                "lang/readme.txt",
                "other/de.properties",
                "lang/..\\escape.properties",
                "lang/en.properties");
        Path languages = temp.resolve("lang");
        BundledLanguages.copyMissing(jar, languages);
        try (var files = Files.list(languages)) {
            assertEquals(
                    List.of("en.properties"),
                    files.map(p -> p.getFileName().toString()).toList());
        }
        assertFalse(Files.exists(temp.resolve("escape.properties")));
    }

    @Test
    void bundledLanguagesContainDistinctTreeRejectionMessages() throws Exception {
        for (String language : List.of("en", "zh")) {
            var properties = new java.util.Properties();
            try (var input = getClass().getResourceAsStream("/lang/" + language + ".properties")) {
                assertNotNull(input);
                properties.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
            }
            String tooLarge = properties.getProperty("tree_too_large");
            String discovery = properties.getProperty("tree_discovery_incomplete");
            assertNotNull(tooLarge, language);
            assertNotNull(discovery, language);
            assertTrue(tooLarge.contains("<limit>"), language);
            assertNotEquals(properties.getProperty("hitmaxblock"), tooLarge, language);
            assertNotEquals(properties.getProperty("hitmaxblock"), discovery, language);
        }
    }
}
