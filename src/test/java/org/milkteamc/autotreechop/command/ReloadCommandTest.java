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
 
package org.milkteamc.autotreechop.command;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.MessageKeys;
import org.milkteamc.autotreechop.configuration.ConfigLoadException;
import org.milkteamc.autotreechop.translation.TranslationManager;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;

class ReloadCommandTest {
    private final AutoTreeChop plugin = mock(AutoTreeChop.class);
    private final Config config = mock(Config.class);
    private final TranslationManager translations = mock(TranslationManager.class);
    private final BukkitCommandActor actor = mock(BukkitCommandActor.class);
    private final CommandSender sender = mock(CommandSender.class);

    @BeforeEach
    void setup() {
        when(actor.sender()).thenReturn(sender);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getTranslationManager()).thenReturn(translations);
        when(config.getLocale()).thenReturn(Locale.ENGLISH);
    }

    @Test
    void reportsFailureWithoutReloadingTranslationsOrClaimingSuccess() {
        doThrow(new ConfigLoadException("Invalid config key leaves.batch-size: must be positive"))
                .when(config)
                .load();
        try (var messages = mockStatic(AutoTreeChop.class)) {
            new ReloadCommand(plugin, config).reload(actor);
            messages.verify(() ->
                    AutoTreeChop.sendMessage(eq(sender), eq(MessageKeys.CONFIG_RELOAD_FAILED), any(TagResolver.class)));
            messages.verifyNoMoreInteractions();
        }
        verifyNoInteractions(translations);
    }

    @Test
    void reportsRestartKeysAfterSuccessfulReload() {
        when(config.getRestartRequiredSettings()).thenReturn(List.of("storage.mysql.password"));
        try (var messages = mockStatic(AutoTreeChop.class)) {
            new ReloadCommand(plugin, config).reload(actor);
            messages.verify(() -> AutoTreeChop.sendMessage(sender, MessageKeys.CONFIG_RELOADED));
            messages.verify(() -> AutoTreeChop.sendMessage(
                    eq(sender), eq(MessageKeys.CONFIG_RESTART_REQUIRED), any(TagResolver.class)));
            messages.verifyNoMoreInteractions();
        }
        verify(translations).reload(Locale.ENGLISH, false);
    }

    @Test
    void doesNotClaimARestartIsNeededForOrdinaryChanges() {
        when(config.getRestartRequiredSettings()).thenReturn(List.of());
        try (var messages = mockStatic(AutoTreeChop.class)) {
            new ReloadCommand(plugin, config).reload(actor);
            messages.verify(() -> AutoTreeChop.sendMessage(sender, MessageKeys.CONFIG_RELOADED));
            messages.verifyNoMoreInteractions();
        }
        verify(translations).reload(Locale.ENGLISH, false);
    }
}
