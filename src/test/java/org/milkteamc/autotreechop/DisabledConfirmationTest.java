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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.milkteamc.autotreechop.command.ConfirmCommand;
import org.milkteamc.autotreechop.database.DataManager;
import org.milkteamc.autotreechop.database.DatabaseManager;
import org.milkteamc.autotreechop.events.PlayerSneakListener;
import org.milkteamc.autotreechop.hooks.HookManager;
import org.milkteamc.autotreechop.utils.ConfirmationManager;
import org.milkteamc.autotreechop.utils.TreeChopUtils;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;

class DisabledConfirmationTest {
    private final UUID uuid = UUID.randomUUID();
    private final AutoTreeChop plugin = mock(AutoTreeChop.class);
    private final Config config = mock(Config.class);
    private final Player player = mock(Player.class);
    private final PlayerConfig data =
            new PlayerConfig(uuid, new DatabaseManager.PlayerData(uuid, true, 0, 0, LocalDate.now()));
    private final ConfirmationManager confirmations = new ConfirmationManager(plugin);
    private final TreeChopUtils chopping = mock(TreeChopUtils.class);
    private final World world = mock(World.class);
    private final Location location = new Location(world, 0, 64, 0);

    @BeforeEach
    void setup() {
        when(plugin.getPluginConfig()).thenReturn(config);
        when(plugin.getConfirmationManager()).thenReturn(confirmations);
        when(plugin.getTreeChopUtils()).thenReturn(chopping);
        when(plugin.getHookManager()).thenReturn(mock(HookManager.class));
        var manager = mock(DataManager.class);
        when(plugin.getDataManager()).thenReturn(manager);
        when(manager.getPlayerConfig(uuid)).thenReturn(data);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.hasPermission("autotreechop.use")).thenReturn(true);
        when(config.getSneakToggle()).thenReturn(true);
        when(config.getConfirmationWindowSeconds()).thenReturn(30);
        var block = mock(Block.class);
        when(block.getType()).thenReturn(Material.OAK_LOG);
        when(world.getBlockAt(location)).thenReturn(block);
        when(config.getLogTypes()).thenReturn(java.util.Set.of(Material.OAK_LOG));
        confirmations.setPendingConfirmation(uuid, ConfirmationManager.ConfirmReason.FLOATING, location, null, true);
    }

    @Test
    void releasingSneakClearsPendingConfirmationEvenAfterReenabling() {
        var listener = new PlayerSneakListener(plugin);
        listener.onPlayerToggleSneak(new PlayerToggleSneakEvent(player, false));
        assertFalse(data.isAutoTreeChopEnabled());
        listener.onPlayerToggleSneak(new PlayerToggleSneakEvent(player, true));
        assertTrue(data.isAutoTreeChopEnabled());
        assertNull(confirmations.consumePendingConfirmation(uuid));
    }

    @Test
    void disabledPlayerCannotConfirmAnOldTarget() {
        data.setAutoTreeChopEnabled(false);
        var actor = mock(BukkitCommandActor.class);
        when(actor.sender()).thenReturn(player);
        new ConfirmCommand(plugin).confirm(actor);
        verifyNoInteractions(chopping);
        assertNull(confirmations.consumePendingConfirmation(uuid));
    }

    @Test
    void enabledPlayerCanStillConfirmTheSavedTarget() {
        var actor = mock(BukkitCommandActor.class);
        when(actor.sender()).thenReturn(player);
        new ConfirmCommand(plugin).confirm(actor);
        verify(chopping)
                .chopTree(
                        eq(world.getBlockAt(location)),
                        eq(player),
                        eq(false),
                        isNull(),
                        eq(location),
                        eq(config),
                        eq(data),
                        any(),
                        eq(true),
                        eq(ConfirmationManager.ConfirmReason.FLOATING));
        assertNull(confirmations.consumePendingConfirmation(uuid));
    }
}
