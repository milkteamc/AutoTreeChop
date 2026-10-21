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
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.milkteamc.autotreechop.AutoTreeChopAPI.PlayerPolicy;
import org.milkteamc.autotreechop.configuration.ConfigLoadException;

class AutoTreeChopPolicyAPITest {
    @TempDir
    Path temp;

    private final AutoTreeChop plugin = mock(AutoTreeChop.class);
    private final Player player = mock(Player.class);
    private final Set<String> permissions = new HashSet<>();
    private final AutoTreeChopAPI api = new AutoTreeChopAPI(plugin);
    private Config config;

    @BeforeEach
    void setup() {
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getResource("config.yml")).thenAnswer(call -> getClass().getResourceAsStream("/config.yml"));
        config = new Config(plugin);
        when(plugin.getPluginConfig()).thenReturn(config);
        when(plugin.isEnabled()).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        when(player.isPermissionSet(anyString())).thenAnswer(call -> permissions.contains(call.getArgument(0)));
        when(player.hasPermission(anyString())).thenAnswer(call -> permissions.contains(call.getArgument(0)));
    }

    private void reload(String contents) throws Exception {
        Files.writeString(temp.resolve("config.yml"), contents);
        config.load();
    }

    @Test
    void resolvesCurrentPermissionsAndReloadsWithoutChangingEarlierSnapshots() throws Exception {
        reload("groups: {builder: {priority: 10, max-blocks-per-day: 800}, donor: {priority: 20, limit-usage: false}}");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            permissions.add("autotreechop.group.builder");
            PlayerPolicy builder = api.getPlayerPolicy(player).orElseThrow();
            assertEquals(new PlayerPolicy("builder", false, 50, 800, 5), builder);
            permissions.add("autotreechop.group.donor");
            assertEquals(
                    new PlayerPolicy("donor", true, 50, 500, 5),
                    api.getPlayerPolicy(player).orElseThrow());
            permissions.remove("autotreechop.group.donor");
            reload("groups: {default: {cooldown-seconds: 3}, builder: {max-uses-per-day: 12}}");
            PlayerPolicy reloaded = api.getPlayerPolicy(player).orElseThrow();
            assertEquals(new PlayerPolicy("builder", false, 12, 500, 3), reloaded);
            assertEquals(new PlayerPolicy("builder", false, 50, 800, 5), builder);
            assertThrows(ConfigLoadException.class, () -> reload("groups: {builder: {cooldown-seconds: -1}}"));
            assertEquals(reloaded, api.getPlayerPolicy(player).orElseThrow());
            reload("groups: {}");
            assertEquals(
                    new PlayerPolicy("default", false, 50, 500, 5),
                    api.getPlayerPolicy(player).orElseThrow());
        }
    }

    @Test
    void reportsLegacyVipAndGlobalUnlimitedWhilePreservingCooldown() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            permissions.add("autotreechop.vip");
            assertEquals(
                    new PlayerPolicy("vip", true, 100, 1000, 2),
                    api.getPlayerPolicy(player).orElseThrow());
            permissions.add("autotreechop.group.restricted");
            reload("groups: {restricted: {max-uses-per-day: 0, max-blocks-per-day: 0, cooldown-seconds: 9}}");
            assertEquals(
                    new PlayerPolicy("restricted", false, 0, 0, 9),
                    api.getPlayerPolicy(player).orElseThrow());
            reload("chopping: {limit-usage: false}\ngroups: {restricted: {cooldown-seconds: 9}}");
            assertEquals(
                    new PlayerPolicy("restricted", true, 50, 500, 9),
                    api.getPlayerPolicy(player).orElseThrow());
        }
    }

    @Test
    void policyDoesNotRequireOrLoadPlayerData() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertTrue(api.getPlayerPolicy(player).isPresent());
            verify(plugin, never()).getDataManager();
        }
    }

    @Test
    void offlinePlayerReturnsEmptyWithoutReadingPermissions() {
        when(player.isOnline()).thenReturn(false);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertTrue(api.getPlayerPolicy(player).isEmpty());
            verify(player, never()).hasPermission(anyString());
            verify(player, never()).isPermissionSet(anyString());
        }
    }

    @Test
    void unavailableConfigOrPluginReturnsEmptyWithoutAccessingThePlayer() {
        when(plugin.getPluginConfig()).thenReturn(null);
        assertTrue(api.getPlayerPolicy(player).isEmpty());
        when(plugin.getPluginConfig()).thenReturn(config);
        when(plugin.isEnabled()).thenReturn(false);
        assertTrue(api.getPlayerPolicy(player).isEmpty());
        when(plugin.isEnabled()).thenReturn(true);
        api.deactivate();
        assertTrue(api.getPlayerPolicy(player).isEmpty(), "Cached API stays inactive after re-enable");
        verifyNoInteractions(player);
    }

    @Test
    void paperRejectsAsyncCallsBeforeAccessingThePlayer() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            assertThrows(IllegalStateException.class, () -> api.getPlayerPolicy(player));
            verifyNoInteractions(player);
        }
    }

    @Test
    void foliaRequiresPlayerOwnershipEvenIfBukkitReportsThePrimaryThread() {
        try (var bukkit = mockStatic(Bukkit.class);
                var platform = mockStatic(AutoTreeChop.class)) {
            platform.when(AutoTreeChop::isFolia).thenReturn(true);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(player)).thenReturn(false);
            assertThrows(IllegalStateException.class, () -> api.getPlayerPolicy(player));
            verifyNoInteractions(player);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(player)).thenReturn(true);
            assertTrue(api.getPlayerPolicy(player).isPresent());
        }
    }

    @Test
    void nullPlayerIsRejectedEvenWhenUnavailable() {
        assertThrows(NullPointerException.class, () -> api.getPlayerPolicy(null));
        api.deactivate();
        assertThrows(NullPointerException.class, () -> api.getPlayerPolicy(null));
    }
}
