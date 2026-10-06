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

import java.time.LocalDate;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.milkteamc.autotreechop.PlayerPreferences.Activation;
import org.milkteamc.autotreechop.PlayerPreferences.Toggle;
import org.milkteamc.autotreechop.command.SettingsCommand;
import org.milkteamc.autotreechop.command.ToggleCommand;
import org.milkteamc.autotreechop.configuration.ActivationMode;
import org.milkteamc.autotreechop.database.DataManager;
import org.milkteamc.autotreechop.database.DatabaseManager;
import org.milkteamc.autotreechop.events.PlayerHotkeyListener;
import org.milkteamc.autotreechop.events.PlayerSneakListener;
import org.milkteamc.autotreechop.utils.ActivationUtils;
import org.milkteamc.autotreechop.utils.ConfirmationManager;
import org.milkteamc.autotreechop.utils.PreferenceUtils;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;

class PlayerPreferencesTest {
    private final UUID uuid = UUID.randomUUID();
    private final AutoTreeChop plugin = mock(AutoTreeChop.class);
    private final Config config = mock(Config.class);
    private final Player player = mock(Player.class);
    private final ConfirmationManager confirmations = mock(ConfirmationManager.class);
    private final DataManager manager = new DataManager(plugin, null, confirmations);
    private final PlayerConfig data =
            new PlayerConfig(uuid, new DatabaseManager.PlayerData(uuid, false, 3, 15, LocalDate.now()));
    private final AutoTreeChopAPI api = new AutoTreeChopAPI(plugin);
    private final BukkitCommandActor actor = mock(BukkitCommandActor.class);

    @BeforeEach
    void setup() {
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getPluginConfig()).thenReturn(config);
        when(plugin.getDataManager()).thenReturn(manager);
        when(plugin.getAutoTreeChopAPI()).thenReturn(api);
        when(plugin.getConfirmationManager()).thenReturn(confirmations);
        when(config.getActivationMode()).thenReturn(ActivationMode.COMMAND);
        when(config.isLeafRemovalEnabled()).thenReturn(true);
        when(config.isAutoReplantEnabled()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(actor.sender()).thenReturn(player);
        manager.addPlayerConfig(uuid, data);
    }

    @Test
    void defaultsFollowServerChangesButOverridesDoNot() {
        assertEquals(ActivationMode.COMMAND, PreferenceUtils.activation(data.getPreferences(), config));
        when(config.getActivationMode()).thenReturn(ActivationMode.SNEAK);
        assertEquals(ActivationMode.SNEAK, PreferenceUtils.activation(data.getPreferences(), config));
        api.setPlayerPreferences(uuid, PlayerPreferences.DEFAULTS.withActivation(Activation.HOTKEY));
        assertEquals(ActivationMode.HOTKEY, PreferenceUtils.activation(data.getPreferences(), config));
        when(config.getActivationMode()).thenReturn(ActivationMode.COMMAND_AND_SNEAK);
        assertEquals(ActivationMode.HOTKEY, PreferenceUtils.activation(data.getPreferences(), config));
        api.setPlayerPreferences(uuid, PlayerPreferences.DEFAULTS);
        assertEquals(ActivationMode.COMMAND_AND_SNEAK, PreferenceUtils.activation(data.getPreferences(), config));
    }

    @Test
    void playerCannotOverrideDisabledFeaturesOrFeaturePermissions() {
        var preferences = new PlayerPreferences(Activation.HOTKEY, Toggle.ON, Toggle.ON, Toggle.ON);
        data.setPreferences(preferences);
        when(config.getActivationMode()).thenReturn(ActivationMode.DISABLED);
        data.setAutoTreeChopEnabled(true);
        assertFalse(ActivationUtils.isActive(player, data, config));
        when(config.isLeafRemovalEnabled()).thenReturn(false);
        when(config.isAutoReplantEnabled()).thenReturn(false);
        assertFalse(PreferenceUtils.leafRemoval(player, preferences, config));
        assertFalse(PreferenceUtils.autoReplant(player, preferences, config));
        when(config.isLeafRemovalEnabled()).thenReturn(true);
        when(config.isAutoReplantEnabled()).thenReturn(true);
        when(player.hasPermission("autotreechop.leaves")).thenReturn(false);
        when(player.hasPermission("autotreechop.replant")).thenReturn(false);
        assertFalse(PreferenceUtils.leafRemoval(player, preferences, config));
        assertFalse(PreferenceUtils.autoReplant(player, preferences, config));
    }

    @Test
    void preferencesAreDetachedPersistableAndDoNotResetUsage() {
        var snapshot = api.getPlayerPreferences(uuid).orElseThrow();
        assertEquals(AutoTreeChopAPI.ChangeResult.UNCHANGED, api.setPlayerPreferences(uuid, snapshot));
        assertFalse(data.isDirty());
        var next = snapshot.withActivation(Activation.SNEAK).withLeafRemoval(Toggle.OFF);
        assertEquals(AutoTreeChopAPI.ChangeResult.UPDATED, api.setPlayerPreferences(uuid, next));
        assertEquals(PlayerPreferences.DEFAULTS, snapshot);
        assertEquals(next, data.popSnapshotIfDirty().getPreferences());
        assertEquals(3, data.getDailyUses());
        assertEquals(15, data.getDailyBlocksBroken());
        verify(confirmations).clearPlayer(uuid);
        clearInvocations(confirmations);
        api.setPlayerPreferences(uuid, next.withAutoReplant(Toggle.OFF));
        verifyNoInteractions(confirmations);
    }

    @Test
    void playerHotkeyAndCommandsUseThePersonalMode() {
        api.setPlayerPreferences(uuid, PlayerPreferences.DEFAULTS.withActivation(Activation.HOTKEY));
        new ToggleCommand(plugin).root(actor);
        assertFalse(data.isAutoTreeChopEnabled());
        when(player.isSneaking()).thenReturn(true);
        var event = new PlayerSwapHandItemsEvent(player, null, null);
        new PlayerHotkeyListener(plugin).onSwapHandItems(event);
        assertTrue(event.isCancelled());
        assertTrue(data.isAutoTreeChopEnabled());
        api.setPlayerPreferences(uuid, PlayerPreferences.DEFAULTS);
        new ToggleCommand(plugin).root(actor);
        assertFalse(data.isAutoTreeChopEnabled());
    }

    @Test
    void sneakMessageOverrideCanSilenceOrEnableTheHint() {
        data.setPreferences(
                PlayerPreferences.DEFAULTS.withActivation(Activation.SNEAK).withSneakMessages(Toggle.OFF));
        when(config.getSneakMessage()).thenReturn(true);
        var listener = new PlayerSneakListener(plugin);
        try (var messages = mockStatic(AutoTreeChop.class)) {
            listener.onPlayerToggleSneak(new PlayerToggleSneakEvent(player, true));
            messages.verifyNoInteractions();
            data.setPreferences(data.getPreferences().withSneakMessages(Toggle.ON));
            when(config.getSneakMessage()).thenReturn(false);
            listener.onPlayerToggleSneak(new PlayerToggleSneakEvent(player, true));
            messages.verify(() -> AutoTreeChop.sendMessage(player, MessageKeys.SNEAK_ENABLED));
        }
    }

    @Test
    void commandUpdatesOneSettingResetsOneOrAllAndRejectsInvalidInput() {
        var command = new SettingsCommand(plugin);
        command.settings(actor, "leaves", "off");
        command.settings(actor, "activation", "hotkey");
        command.settings(actor, "sneak-messages", "on");
        assertEquals(
                new PlayerPreferences(Activation.HOTKEY, Toggle.ON, Toggle.OFF, Toggle.DEFAULT), data.getPreferences());
        command.settings(actor, "activation", "default");
        assertEquals(Activation.DEFAULT, data.getPreferences().activation());
        assertEquals(Toggle.OFF, data.getPreferences().leafRemoval());
        var before = data.getPreferences();
        command.settings(actor, "activation", "on");
        command.settings(actor, "unknown", "off");
        command.settings(actor, "reset", "all");
        command.settings(actor, "leaves", null);
        assertEquals(before, data.getPreferences());
        command.settings(actor, "reset", null);
        assertEquals(PlayerPreferences.DEFAULTS, data.getPreferences());
    }

    @Test
    void commandCannotChangeSettingsWithoutPermissionOrLoadedData() {
        var command = new SettingsCommand(plugin);
        when(player.hasPermission("autotreechop.settings")).thenReturn(false);
        command.settings(actor, "activation", "hotkey");
        assertEquals(PlayerPreferences.DEFAULTS, data.getPreferences());
        when(player.hasPermission("autotreechop.settings")).thenReturn(true);
        when(player.hasPermission("autotreechop.use")).thenReturn(false);
        command.settings(actor, "activation", "hotkey");
        assertEquals(PlayerPreferences.DEFAULTS, data.getPreferences());
        when(player.hasPermission("autotreechop.use")).thenReturn(true);
        manager.removePlayerConfig(uuid);
        command.settings(actor, "activation", "hotkey");
        assertNull(manager.getPlayerConfig(uuid));
    }

    @Test
    void adminCanChangeAndResetAnotherLoadedPlayerWithoutSelfSettingsPermission() {
        var target = mock(Player.class);
        var targetId = UUID.randomUUID();
        var targetData =
                new PlayerConfig(targetId, new DatabaseManager.PlayerData(targetId, true, 9, 42, LocalDate.now()));
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("Target");
        when(target.hasPermission("autotreechop.leaves")).thenReturn(false);
        manager.addPlayerConfig(targetId, targetData);
        when(player.hasPermission("autotreechop.settings")).thenReturn(false);
        when(player.hasPermission("autotreechop.use")).thenReturn(false);
        when(player.getName()).thenReturn("Admin");

        var command = new SettingsCommand(plugin);
        command.settingsForPlayer(actor, target, "activation", "hotkey");
        command.settingsForPlayer(actor, target, "leaves", "on");
        assertEquals(
                PlayerPreferences.DEFAULTS.withActivation(Activation.HOTKEY).withLeafRemoval(Toggle.ON),
                targetData.getPreferences());
        assertEquals(PlayerPreferences.DEFAULTS, data.getPreferences());
        assertTrue(targetData.isAutoTreeChopEnabled());
        assertEquals(9, targetData.getDailyUses());
        assertEquals(42, targetData.getDailyBlocksBroken());

        command.settingsForPlayer(actor, target, "leaves", "invalid");
        assertEquals(Toggle.ON, targetData.getPreferences().leafRemoval());
        command.settingsForPlayer(actor, target, "reset", null);
        assertEquals(PlayerPreferences.DEFAULTS, targetData.getPreferences());
    }

    @Test
    void adminPermissionAndLoadedTargetAreRequiredEvenFromConsole() {
        var target = mock(Player.class);
        var targetId = UUID.randomUUID();
        var targetData =
                new PlayerConfig(targetId, new DatabaseManager.PlayerData(targetId, false, 0, 0, LocalDate.now()));
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("Target");
        manager.addPlayerConfig(targetId, targetData);
        var console = mock(CommandSender.class);
        when(console.getName()).thenReturn("CONSOLE");
        when(actor.sender()).thenReturn(console);
        var command = new SettingsCommand(plugin);

        command.settingsForPlayer(actor, target, "replant", "off");
        assertEquals(PlayerPreferences.DEFAULTS, targetData.getPreferences());
        when(console.hasPermission("autotreechop.settings.other")).thenReturn(true);
        command.settingsForPlayer(actor, target, "replant", "off");
        assertEquals(Toggle.OFF, targetData.getPreferences().autoReplant());
        manager.removePlayerConfig(targetId);
        command.settingsForPlayer(actor, target, "activation", "sneak");
        assertNull(manager.getPlayerConfig(targetId));
    }

    @Test
    void settingsQueryReportsEffectiveValuesAndRejectsTheWrongContext() {
        var preferences = new PlayerPreferences(Activation.SNEAK, Toggle.ON, Toggle.OFF, Toggle.ON);
        api.setPlayerPreferences(uuid, preferences);
        try (var bukkit = mockStatic(Bukkit.class)) {
            assertThrows(IllegalStateException.class, () -> api.getPlayerSettings(player));
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertEquals(
                    new AutoTreeChopAPI.PlayerSettings(Activation.SNEAK, true, false, true),
                    api.getPlayerSettings(player).orElseThrow());
            when(config.isAutoReplantEnabled()).thenReturn(false);
            assertFalse(api.getPlayerSettings(player).orElseThrow().autoReplant());
            when(player.isOnline()).thenReturn(false);
            assertTrue(api.getPlayerSettings(player).isEmpty());
        }
    }

    @Test
    void unavailableAndDeactivatedApiCannotReadOrUpdatePreferences() {
        manager.removePlayerConfig(uuid);
        assertTrue(api.getPlayerPreferences(uuid).isEmpty());
        assertEquals(
                AutoTreeChopAPI.ChangeResult.UNAVAILABLE, api.setPlayerPreferences(uuid, PlayerPreferences.DEFAULTS));
        manager.addPlayerConfig(uuid, data);
        api.deactivate();
        assertTrue(api.getPlayerPreferences(uuid).isEmpty());
        assertTrue(api.getPlayerSettings(player).isEmpty());
        assertEquals(
                AutoTreeChopAPI.ChangeResult.UNAVAILABLE, api.setPlayerPreferences(uuid, PlayerPreferences.DEFAULTS));
    }

    @Test
    void preferenceApiRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> api.getPlayerPreferences(null));
        assertThrows(NullPointerException.class, () -> api.setPlayerPreferences(uuid, null));
        assertThrows(NullPointerException.class, () -> api.setPlayerPreferences(null, PlayerPreferences.DEFAULTS));
        assertThrows(NullPointerException.class, () -> api.getPlayerSettings(null));
    }
}
