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
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.SimpleServicesManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.milkteamc.autotreechop.database.DataManager;
import org.milkteamc.autotreechop.database.DatabaseManager;
import org.milkteamc.autotreechop.utils.ConfirmationManager;

class AutoTreeChopAPITest {
    private final UUID uuid = UUID.randomUUID();
    private final AutoTreeChop plugin = mock(AutoTreeChop.class);
    private final ConfirmationManager confirmations = mock(ConfirmationManager.class);
    private final DataManager data = new DataManager(plugin, null, confirmations);
    private final AutoTreeChopAPI api = new AutoTreeChopAPI(plugin);

    @BeforeEach
    void setup() {
        when(plugin.getDataManager()).thenReturn(data);
        when(plugin.getConfirmationManager()).thenReturn(confirmations);
        when(plugin.isEnabled()).thenReturn(true);
    }

    private PlayerConfig addPlayer(boolean enabled, LocalDate date) {
        PlayerConfig config = new PlayerConfig(uuid, new DatabaseManager.PlayerData(uuid, enabled, 3, 20, date));
        data.addPlayerConfig(uuid, config);
        return config;
    }

    @Test
    void unavailableIsDistinctFromDisabledAndZeroUsage() {
        assertTrue(api.getPlayerState(uuid).isEmpty());
        assertFalse(api.isPlayerDataReady(uuid));
        assertEquals(AutoTreeChopAPI.ChangeResult.UNAVAILABLE, api.setAutoTreeChopEnabled(uuid, true));
        assertNull(data.getPlayerConfig(uuid));
        verifyNoInteractions(confirmations);
        addPlayer(false, LocalDate.now().minusDays(1));
        assertEquals(
                new AutoTreeChopAPI.PlayerState(false, 0, 0),
                api.getPlayerState(uuid).orElseThrow());
        assertTrue(api.isPlayerDataReady(uuid));
    }

    @Test
    void snapshotIsImmutableAndDoesNotConsumeDirtyState() {
        PlayerConfig config = addPlayer(true, LocalDate.now());
        var state = api.getPlayerState(uuid).orElseThrow();
        config.incrementDailyUses();
        config.incrementDailyBlocksBroken();
        assertEquals(new AutoTreeChopAPI.PlayerState(true, 3, 20), state);
        assertEquals(
                new AutoTreeChopAPI.PlayerState(true, 4, 21),
                api.getPlayerState(uuid).orElseThrow());
        assertTrue(config.isDirty());
        assertNotNull(config.popSnapshotIfDirty());
    }

    @Test
    void dailyCountersResetOnTheServersLocalDate() {
        PlayerConfig config = addPlayer(true, LocalDate.now().minusDays(1));
        assertEquals(
                new AutoTreeChopAPI.PlayerState(true, 0, 0),
                api.getPlayerState(uuid).orElseThrow());
        assertTrue(config.isDirty());
    }

    @Test
    void updatesArePersistableAndRepeatedEnableIsUnchanged() {
        PlayerConfig config = addPlayer(false, LocalDate.now());
        assertEquals(AutoTreeChopAPI.ChangeResult.UPDATED, api.setAutoTreeChopEnabled(uuid, true));
        assertTrue(config.popSnapshotIfDirty().isAutoTreeChopEnabled());
        assertEquals(AutoTreeChopAPI.ChangeResult.UNCHANGED, api.setAutoTreeChopEnabled(uuid, true));
        assertFalse(config.isDirty());
        verifyNoInteractions(confirmations);
    }

    @Test
    void disableClearsConfirmationEvenWhenAlreadyDisabled() {
        PlayerConfig config = addPlayer(true, LocalDate.now());
        assertEquals(AutoTreeChopAPI.ChangeResult.UPDATED, api.setAutoTreeChopEnabled(uuid, false));
        assertFalse(config.isAutoTreeChopEnabled());
        assertEquals(AutoTreeChopAPI.ChangeResult.UNCHANGED, api.setAutoTreeChopEnabled(uuid, false));
        verify(confirmations, times(2)).clearPlayer(uuid);
    }

    @Test
    void leavingBetweenReadinessCheckAndMutationReturnsUnavailable() {
        addPlayer(false, LocalDate.now());
        assertTrue(api.isPlayerDataReady(uuid));
        data.removePlayerConfig(uuid);
        assertEquals(AutoTreeChopAPI.ChangeResult.UNAVAILABLE, api.setAutoTreeChopEnabled(uuid, true));
        assertTrue(api.getPlayerState(uuid).isEmpty());
    }

    @Test
    void legacyMethodsKeepTheirUnavailableDefaults() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        assertFalse(api.isAutoTreeChopEnabled(player));
        assertEquals(0, api.getPlayerDailyUses(uuid));
        assertEquals(0, api.getPlayerDailyBlocksBroken(uuid));
        api.enableAutoTreeChop(player);
        api.disableAutoTreeChop(player);
        assertNull(data.getPlayerConfig(uuid));
        addPlayer(false, LocalDate.now());
        api.enableAutoTreeChop(player);
        assertTrue(api.isAutoTreeChopEnabled(player));
        assertEquals(3, api.getPlayerDailyUses(uuid));
        assertEquals(20, api.getPlayerDailyBlocksBroken(uuid));
        api.disableAutoTreeChop(player);
        assertFalse(api.isAutoTreeChopEnabled(player));
        verify(confirmations).clearPlayer(uuid);
    }

    @Test
    void disabledOrUninitializedPluginIsUnavailable() {
        addPlayer(true, LocalDate.now());
        when(plugin.isEnabled()).thenReturn(false);
        assertTrue(api.getPlayerState(uuid).isEmpty());
        assertEquals(AutoTreeChopAPI.ChangeResult.UNAVAILABLE, api.setAutoTreeChopEnabled(uuid, false));
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getDataManager()).thenReturn(null);
        assertTrue(api.getPlayerState(uuid).isEmpty());
        assertEquals(AutoTreeChopAPI.ChangeResult.UNAVAILABLE, api.setAutoTreeChopEnabled(uuid, true));
    }

    @Test
    void nullArgumentsFailExplicitly() {
        assertThrows(NullPointerException.class, () -> new AutoTreeChopAPI(null));
        assertThrows(NullPointerException.class, () -> api.getPlayerState(null));
        assertThrows(NullPointerException.class, () -> api.setAutoTreeChopEnabled(null, true));
        assertThrows(NullPointerException.class, () -> api.enableAutoTreeChop(null));
    }

    @Test
    void serviceIsDiscoverableAndDisableInvalidatesCachedReference() {
        Server server = mock(Server.class);
        SimpleServicesManager services = new SimpleServicesManager();
        when(plugin.getServer()).thenReturn(server);
        when(server.getServicesManager()).thenReturn(services);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        doCallRealMethod().when(plugin).registerApi();
        doCallRealMethod().when(plugin).getAutoTreeChopAPI();
        doCallRealMethod().when(plugin).onDisable();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            plugin.registerApi();
            AutoTreeChopAPI cached = services.load(AutoTreeChopAPI.class);
            assertSame(plugin.getAutoTreeChopAPI(), cached);
            addPlayer(true, LocalDate.now());
            assertTrue(cached.isPlayerDataReady(uuid));
            plugin.onDisable();
            assertNull(services.load(AutoTreeChopAPI.class));
            assertNull(plugin.getAutoTreeChopAPI());
            assertTrue(cached.getPlayerState(uuid).isEmpty());
            assertEquals(AutoTreeChopAPI.ChangeResult.UNAVAILABLE, cached.setAutoTreeChopEnabled(uuid, false));
        }
    }
}
