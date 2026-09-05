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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.milkteamc.autotreechop.AutoTreeChop;

class TranslationManagerTest {
    @TempDir
    Path temp;

    private final AutoTreeChop plugin = mock(AutoTreeChop.class);
    private final Map<String, String> resources = new HashMap<>();
    private TranslationManager manager;

    @BeforeEach
    void setup() throws Exception {
        Files.createDirectory(temp.resolve("lang"));
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getResource(anyString())).thenAnswer(call -> {
            String value = resources.get(call.getArgument(0));
            return value == null ? null : new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        });
        write("styles", "prefix=<green>\nstyleOnly=not a translation");
        write("en", "hello=Hello\nmissing=English fallback");
        try (var audiences = mockStatic(BukkitAudiences.class)) {
            audiences.when(() -> BukkitAudiences.create(plugin)).thenReturn(mock(BukkitAudiences.class));
            manager = new TranslationManager(plugin);
        }
    }

    private void write(String locale, String content) throws Exception {
        Files.writeString(temp.resolve("lang/" + locale + ".properties"), content);
    }

    @AfterEach
    void close() {
        if (manager != null) manager.close();
    }

    @Test
    void discoversCustomLocaleAndUsesClientRegionOrLanguage() throws Exception {
        write("pt_BR", "hello=Olá");
        write("pt", "hello=Português");
        manager.initialize(Locale.ENGLISH, true);
        Player player = mock(Player.class);
        when(player.locale()).thenReturn(Locale.forLanguageTag("pt-BR"));
        assertEquals("Olá", manager.getMessage(player, "hello"));
        when(player.locale()).thenReturn(Locale.forLanguageTag("pt-PT"));
        assertEquals("Português", manager.getMessage(player, "hello"));
        assertEquals("English fallback", manager.getMessage(player, "missing"));
    }

    @Test
    void reloadDiscoversAddedFilesAndDropsDeletedCustomLocales() throws Exception {
        manager.initialize(Locale.ENGLISH, true);
        write("ko", "hello=안녕하세요");
        manager.reload(Locale.ENGLISH, true);
        Player player = mock(Player.class);
        when(player.locale()).thenReturn(Locale.KOREAN);
        assertEquals("안녕하세요", manager.getMessage(player, "hello"));
        Files.delete(temp.resolve("lang/ko.properties"));
        manager.reload(Locale.ENGLISH, true);
        assertEquals("Hello", manager.getMessage(player, "hello"));
        verify(plugin, times(3)).saveBundledLanguages();
    }

    @Test
    void reloadReadsEditsAndRespectsConfiguredLocaleWhenClientLocaleDisabled() throws Exception {
        write("it", "hello=Ciao");
        manager.initialize(Locale.ITALIAN, false);
        Player player = mock(Player.class);
        when(player.locale()).thenReturn(Locale.ENGLISH);
        assertEquals("Ciao", manager.getMessage(player, "hello"));
        write("it", "hello=Salve");
        manager.reload(Locale.ITALIAN, false);
        assertEquals("Salve", manager.getMessage(player, "hello"));
    }

    @Test
    void addsMissingBundledKeysWithoutReplacingUserOverrides() throws Exception {
        write("tr", "hello=custom");
        resources.put("lang/tr.properties", "hello=bundled\nnewKey=new translation");
        manager.initialize(Locale.forLanguageTag("tr"), false);
        assertEquals("custom", manager.getMessage("hello", Locale.forLanguageTag("tr")));
        assertEquals("new translation", manager.getMessage("newKey", Locale.forLanguageTag("tr")));
        assertTrue(Files.readString(temp.resolve("lang/tr.properties")).contains("hello=custom"));
    }

    @Test
    void malformedFilesAndDirectoriesDoNotBreakOtherLanguages() throws Exception {
        write("bad!", "invalidOnly=ignored");
        write("fr", "hello=" + "\\" + "uNOTHEX");
        resources.put("lang/fr.properties", "hello=Bonjour");
        Files.createDirectory(temp.resolve("lang/de.properties"));
        manager.initialize(Locale.ENGLISH, false);
        assertEquals("Hello", manager.getMessage("hello", Locale.FRENCH));
        assertEquals("[Missing: invalidOnly]", manager.getMessage("invalidOnly", Locale.ENGLISH));
        assertEquals("[Missing: styleOnly]", manager.getMessage("styleOnly", Locale.ENGLISH));
    }

    @Test
    void duplicateNormalizedLocalesUseDeterministicFilenameOrder() throws Exception {
        write("pt_BR", "hello=underscore");
        write("pt-BR", "hello=hyphen");
        manager.initialize(Locale.ENGLISH, false);
        assertEquals("hyphen", manager.getMessage("hello", Locale.forLanguageTag("pt-BR")));
        manager.reload(Locale.ENGLISH, false);
        assertEquals("hyphen", manager.getMessage("hello", Locale.forLanguageTag("pt-BR")));
    }
}
