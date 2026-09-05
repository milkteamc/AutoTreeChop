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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

/** Discovers language resources from the plugin JAR, including JARs without directory entries. */
public final class BundledLanguages {
    private BundledLanguages() {}

    public static void copyMissing(Path pluginJar, Path langFolder) throws IOException {
        Files.createDirectories(langFolder);
        try (JarFile jar = new JarFile(pluginJar.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith("lang/") || !name.endsWith(".properties")) continue;
                String fileName = name.substring("lang/".length());
                // Language files live directly under lang/; ignore nested paths and traversal entries.
                if (fileName.contains("/") || fileName.contains("\\")) continue;
                Path target = langFolder.resolve(fileName);
                if (Files.exists(target)) continue;
                try (InputStream source = jar.getInputStream(entry)) {
                    try {
                        Files.copy(source, target);
                    } catch (FileAlreadyExistsException ignored) {
                        // Another task created the file: never overwrite it.
                    }
                }
            }
        }
    }
}
