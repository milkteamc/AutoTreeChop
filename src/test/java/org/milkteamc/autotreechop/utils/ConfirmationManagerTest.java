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
 
package org.milkteamc.autotreechop.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.utils.ConfirmationManager.ConfirmReason;

class ConfirmationManagerTest {
    private final AutoTreeChop plugin = mock(AutoTreeChop.class);
    private final Config config = mock(Config.class);
    private final UUID uuid = UUID.randomUUID();
    private final Location target = new Location(null, 0, 64, 0);
    private final ItemStack tool = mock(ItemStack.class);
    private ConfirmationManager manager;

    @BeforeEach
    void setup() {
        when(plugin.getPluginConfig()).thenReturn(config);
        when(config.getConfirmationWindowSeconds()).thenReturn(30);
        manager = new ConfirmationManager(plugin);
    }

    @Test
    void floatingConfirmationCanBeConsumedByPhysicalRetryOrCommandOnlyOnce() {
        manager.setPendingConfirmation(uuid, ConfirmReason.FLOATING, target, tool, true);
        assertEquals(
                ConfirmReason.FLOATING,
                manager.consumePendingConfirmationForBlock(uuid, target, tool).reason());
        assertNull(manager.consumePendingConfirmation(uuid));
        manager.setPendingConfirmation(uuid, ConfirmReason.FLOATING, target, tool, true);
        assertEquals(
                ConfirmReason.FLOATING, manager.consumePendingConfirmation(uuid).reason());
        assertNull(manager.consumePendingConfirmation(uuid));
    }

    @Test
    void physicalRetryMustMatchTargetAndTool() {
        manager.setPendingConfirmation(uuid, ConfirmReason.NO_LEAVES, target, tool, false);
        assertNull(
                manager.consumePendingConfirmationForBlock(uuid, target.clone().add(1, 0, 0), tool));
        assertNull(manager.consumePendingConfirmationForBlock(uuid, target, mock(ItemStack.class)));
        assertEquals(
                ConfirmReason.NO_LEAVES,
                manager.consumePendingConfirmationForBlock(uuid, target, tool).reason());
        assertNull(manager.consumePendingConfirmation(uuid));
    }

    @Test
    void expiredAndDisconnectedPlayersCannotConfirmFloatingTree() {
        when(config.getConfirmationWindowSeconds()).thenReturn(-1);
        manager.setPendingConfirmation(uuid, ConfirmReason.FLOATING, target, tool, true);
        assertNull(manager.consumePendingConfirmation(uuid));
        when(config.getConfirmationWindowSeconds()).thenReturn(30);
        manager.setPendingConfirmation(uuid, ConfirmReason.FLOATING, target, tool, true);
        manager.clearPlayer(uuid);
        assertNull(manager.consumePendingConfirmation(uuid));
    }
}
