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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.milkteamc.autotreechop.command.ToggleCommand;
import org.milkteamc.autotreechop.configuration.ActivationMode;
import org.milkteamc.autotreechop.configuration.ConfigLoadException;
import org.milkteamc.autotreechop.configuration.ConfigSchema;
import org.milkteamc.autotreechop.database.DataManager;
import org.milkteamc.autotreechop.database.DatabaseManager;
import org.milkteamc.autotreechop.events.BlockBreakListener;
import org.milkteamc.autotreechop.events.PlayerHotkeyListener;
import org.milkteamc.autotreechop.events.PlayerSneakListener;
import org.milkteamc.autotreechop.utils.ActivationUtils;
import org.milkteamc.autotreechop.utils.AsyncTaskScheduler;
import org.milkteamc.autotreechop.utils.ConfirmationManager;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;

class ActivationTest {
    @TempDir
    Path temp;

    private final AutoTreeChop plugin = mock(AutoTreeChop.class);
    private final Player player = mock(Player.class);
    private final UUID uuid = UUID.randomUUID();
    private final PlayerConfig data =
            new PlayerConfig(uuid, new DatabaseManager.PlayerData(uuid, false, 0, 0, LocalDate.now()));
    private final DataManager manager = mock(DataManager.class);
    private final ConfirmationManager confirmations = mock(ConfirmationManager.class);
    private Config config;

    @BeforeEach
    void setup() {
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getResource("config.yml")).thenAnswer(call -> getClass().getResourceAsStream("/config.yml"));
        config = new Config(plugin);
        when(plugin.getPluginConfig()).thenReturn(config);
        when(plugin.getDataManager()).thenReturn(manager);
        when(plugin.getConfirmationManager()).thenReturn(confirmations);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.hasPermission("autotreechop.use")).thenReturn(true);
        when(manager.getPlayerConfig(uuid)).thenReturn(data);
    }

    private void mode(ActivationMode mode) throws Exception {
        var yaml = ConfigSchema.parse(Files.readString(temp.resolve("config.yml")));
        yaml.set("activation.mode", mode.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        Files.writeString(temp.resolve("config.yml"), yaml.dump());
        config.load();
    }

    @ParameterizedTest
    @EnumSource(ActivationMode.class)
    void activationMatchesModeWithoutMutatingTheSavedPreference(ActivationMode mode) throws Exception {
        mode(mode);
        for (boolean enabled : new boolean[] {false, true}) {
            data.setAutoTreeChopEnabled(enabled);
            for (boolean sneaking : new boolean[] {false, true}) {
                when(player.isSneaking()).thenReturn(sneaking);
                boolean expected =
                        switch (mode) {
                            case DISABLED -> false;
                            case SNEAK -> sneaking;
                            case COMMAND_AND_SNEAK -> enabled && sneaking;
                            default -> enabled;
                        };
                assertEquals(expected, ActivationUtils.isActive(player, data, config));
                assertEquals(enabled, data.isAutoTreeChopEnabled());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ActivationMode.class)
    void onlyCommandModesAllowSelfCommands(ActivationMode mode) throws Exception {
        mode(mode);
        var actor = mock(BukkitCommandActor.class);
        when(actor.sender()).thenReturn(player);
        var command = new ToggleCommand(plugin);
        boolean allowed = mode == ActivationMode.COMMAND || mode == ActivationMode.COMMAND_AND_SNEAK;
        command.root(actor);
        assertEquals(allowed, data.isAutoTreeChopEnabled());
        data.setAutoTreeChopEnabled(false);
        command.enable(actor);
        assertEquals(allowed, data.isAutoTreeChopEnabled());
        data.setAutoTreeChopEnabled(true);
        command.disable(actor);
        assertEquals(!allowed, data.isAutoTreeChopEnabled());
    }

    @ParameterizedTest
    @EnumSource(
            value = ActivationMode.class,
            names = {"SNEAK", "COMMAND_AND_SNEAK"})
    void holdingSneakNeverOverwritesPreferenceAndReleaseClearsConfirmation(ActivationMode mode) throws Exception {
        mode(mode);
        var listener = new PlayerSneakListener(plugin);
        listener.onPlayerToggleSneak(new PlayerToggleSneakEvent(player, true));
        assertFalse(data.isAutoTreeChopEnabled());
        data.setAutoTreeChopEnabled(true);
        listener.onPlayerToggleSneak(new PlayerToggleSneakEvent(player, false));
        assertTrue(data.isAutoTreeChopEnabled());
        verify(confirmations).clearPlayer(uuid);
    }

    @Test
    void cancelledSneakingDoesNotChangePreferenceOrClearConfirmation() throws Exception {
        mode(ActivationMode.COMMAND_AND_SNEAK);
        data.setAutoTreeChopEnabled(true);
        var event = new PlayerToggleSneakEvent(player, false);
        event.setCancelled(true);
        new PlayerSneakListener(plugin).onPlayerToggleSneak(event);
        assertTrue(data.isAutoTreeChopEnabled());
        verifyNoInteractions(confirmations);
    }

    @Test
    void shiftSwapTogglesWithoutSwappingItemsAndReleaseKeepsPreference() throws Exception {
        mode(ActivationMode.HOTKEY);
        when(player.isSneaking()).thenReturn(true);
        var listener = new PlayerHotkeyListener(plugin);
        var on = new PlayerSwapHandItemsEvent(player, null, null);
        listener.onSwapHandItems(on);
        assertTrue(on.isCancelled());
        assertTrue(data.isAutoTreeChopEnabled());
        new PlayerSneakListener(plugin).onPlayerToggleSneak(new PlayerToggleSneakEvent(player, false));
        assertTrue(data.isAutoTreeChopEnabled());
        var off = new PlayerSwapHandItemsEvent(player, null, null);
        listener.onSwapHandItems(off);
        assertTrue(off.isCancelled());
        assertFalse(data.isAutoTreeChopEnabled());
        verify(confirmations).clearPlayer(uuid);
    }

    @Test
    void ordinarySwapCancelledEventMissingPermissionAndUnloadedDataAreUntouched() throws Exception {
        mode(ActivationMode.HOTKEY);
        var listener = new PlayerHotkeyListener(plugin);
        var ordinary = new PlayerSwapHandItemsEvent(player, null, null);
        listener.onSwapHandItems(ordinary);
        assertFalse(ordinary.isCancelled());
        when(player.isSneaking()).thenReturn(true);
        var cancelled = new PlayerSwapHandItemsEvent(player, null, null);
        cancelled.setCancelled(true);
        listener.onSwapHandItems(cancelled);
        assertFalse(data.isAutoTreeChopEnabled());
        when(player.hasPermission("autotreechop.use")).thenReturn(false);
        var denied = new PlayerSwapHandItemsEvent(player, null, null);
        listener.onSwapHandItems(denied);
        assertFalse(denied.isCancelled());
        when(player.hasPermission("autotreechop.use")).thenReturn(true);
        when(manager.getPlayerConfig(uuid)).thenReturn(null);
        var unavailable = new PlayerSwapHandItemsEvent(player, null, null);
        listener.onSwapHandItems(unavailable);
        assertFalse(unavailable.isCancelled());
        assertFalse(data.isAutoTreeChopEnabled());
        verifyNoInteractions(confirmations);
    }

    @Test
    void reloadDisablesHotkeyImmediatelyWithoutClearingPreference() throws Exception {
        mode(ActivationMode.HOTKEY);
        when(player.isSneaking()).thenReturn(true);
        var listener = new PlayerHotkeyListener(plugin);
        listener.onSwapHandItems(new PlayerSwapHandItemsEvent(player, null, null));
        mode(ActivationMode.COMMAND);
        var event = new PlayerSwapHandItemsEvent(player, null, null);
        listener.onSwapHandItems(event);
        assertFalse(event.isCancelled());
        assertTrue(data.isAutoTreeChopEnabled());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "false", "[]", "unknown", "COMMAND", "'command AND sneak'"})
    void invalidModeKeepsPreviousSettingsAndTheEditedFile(String value) throws Exception {
        mode(ActivationMode.COMMAND_AND_SNEAK);
        String contents = "activation: {mode: " + value + "}";
        Files.writeString(temp.resolve("config.yml"), contents);
        assertThrows(ConfigLoadException.class, config::load);
        assertEquals(ActivationMode.COMMAND_AND_SNEAK, config.getActivationMode());
        assertEquals(contents, Files.readString(temp.resolve("config.yml")));
    }

    @Test
    void newInstallUsesCommandAndOldSneakSwitchBecomesSneakMode() throws Exception {
        assertEquals(ActivationMode.COMMAND, config.getActivationMode());
        Files.writeString(temp.resolve("config.yml"), "activation: {command-toggle: false, sneak-toggle: true}");
        config.load();
        assertEquals(ActivationMode.SNEAK, config.getActivationMode());
        assertFalse(config.isCommandActivationEnabled());
        var migrated = ConfigSchema.parse(Files.readString(temp.resolve("config.yml")));
        assertEquals("sneak", migrated.getString("activation.mode"));
        assertFalse(migrated.contains("activation.command-toggle"));
        assertFalse(migrated.contains("activation.sneak-toggle"));
    }

    @Test
    void deniedPlayerCanBreakNormallyWhileSneaking() throws Exception {
        mode(ActivationMode.SNEAK);
        when(player.isSneaking()).thenReturn(true);
        when(player.hasPermission("autotreechop.use")).thenReturn(false);
        var block = mock(Block.class);
        var event = new BlockBreakEvent(block, player);
        try (var schedulers = mockConstruction(AsyncTaskScheduler.class)) {
            new BlockBreakListener(plugin).onBlockBreak(event);
            assertFalse(event.isCancelled());
            verifyNoInteractions(block, schedulers.constructed().get(0));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void transitionalLegacyModeIsConvertedOnceAndObsoleteFieldsAreRemoved(int mask) throws Exception {
        boolean command = (mask & 1) != 0;
        boolean sneak = (mask & 2) != 0;
        Files.writeString(
                temp.resolve("config.yml"),
                "activation: {mode: legacy, command-toggle: " + command + ", sneak-toggle: " + sneak + "}");
        config.load();
        var file = temp.resolve("config.yml");
        String migrated = Files.readString(file);
        var yaml = ConfigSchema.parse(migrated);
        String expected = command ? (sneak ? "command-and-sneak" : "command") : (sneak ? "sneak" : "disabled");
        assertEquals(expected, yaml.getString("activation.mode"));
        assertFalse(yaml.contains("activation.command-toggle"));
        assertFalse(yaml.contains("activation.sneak-toggle"));
        long before;
        try (var files = Files.list(temp)) {
            before = files.count();
        }
        config.load();
        assertEquals(migrated, Files.readString(file));
        try (var files = Files.list(temp)) {
            assertEquals(before, files.count(), "Reload must not recreate obsolete defaults or another backup");
        }
    }

    @Test
    void explicitModeWinsOverObsoleteSwitchesAndCleansThemUp() throws Exception {
        Files.writeString(temp.resolve("config.yml"), """
                enable-command-toggle: false
                enable-sneak-toggle: true
                activation:
                  mode: hotkey
                  command-toggle: true
                  sneak-toggle: true
                """);
        config.load();
        assertEquals(ActivationMode.HOTKEY, config.getActivationMode());
        var yaml = ConfigSchema.parse(Files.readString(temp.resolve("config.yml")));
        assertFalse(yaml.contains("enable-command-toggle"));
        assertFalse(yaml.contains("enable-sneak-toggle"));
        assertFalse(yaml.contains("activation.command-toggle"));
        assertFalse(yaml.contains("activation.sneak-toggle"));
    }
}
