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

import java.lang.reflect.Method;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.milkteamc.autotreechop.Config;

class NativeMaterialCompatibilityTest {
    // Represent materials supplied by a newer server but absent from the compile-time API and XSeries.
    private Material material(String name) {
        Material material = mock(Material.class);
        when(material.name()).thenReturn(name);
        return material;
    }

    @Test
    void newLogDoesNotRequireAnXSeriesEntryToChooseSingleSapling() throws Exception {
        Method method =
                TreeReplantUtils.class.getDeclaredMethod("isLikely2x2Tree", Material.class, Location.class, Set.class);
        method.setAccessible(true);
        assertFalse((boolean) method.invoke(null, material("POPLAR_LOG"), new Location(null, 0, 100, 0), Set.of()));
    }

    @Test
    void newSoilIsRejectedSafelyUntilExplicitlyAllowed() {
        Material soil = material("FUTURE_SOIL");
        Config config = mock(Config.class);
        when(config.getValidSoilTypes()).thenReturn(Set.of());
        assertFalse(TreeReplantUtils.isValidSoil(soil, config));
        when(config.getValidSoilTypes()).thenReturn(Set.of(soil));
        assertTrue(TreeReplantUtils.isValidSoil(soil, config));
    }

    @Test
    void newBlocksDoNotCrashOrPermitReplacingSurvivingLogs() throws Exception {
        Method method = TreeReplantUtils.class.getDeclaredMethod("isClearForSapling", Block.class);
        method.setAccessible(true);
        Block block = mock(Block.class);
        Material log = material("POPLAR_LOG");
        Material building = material("FUTURE_BUILDING_BLOCK");
        Material leaf = material("RED_POPLAR_LEAVES");
        when(block.getType()).thenReturn(log);
        assertFalse((boolean) method.invoke(null, block));
        when(block.getType()).thenReturn(building);
        assertFalse((boolean) method.invoke(null, block));
        when(block.getType()).thenReturn(leaf);
        assertTrue((boolean) method.invoke(null, block));
    }

    @Test
    void toolNamesRetainXSeriesAliasNormalization() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack item = mock(ItemStack.class);
        Material oldShovel = material("WOOD_SPADE");
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(item);
        when(item.getType()).thenReturn(oldShovel);
        assertTrue(TreeChopUtils.isTool(player));
        when(item.getType()).thenReturn(Material.AIR);
        assertFalse(TreeChopUtils.isTool(player));
    }

    @Test
    void unknownItemsDoNotCrashOrBecomeToolsByNameAlone() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack item = mock(ItemStack.class);
        Material sapling = material("POPLAR_SAPLING");
        Material axe = material("FUTURE_AXE");
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(item);
        when(item.getType()).thenReturn(sapling);
        assertFalse(TreeChopUtils.isTool(player));
        when(item.getType()).thenReturn(axe);
        assertFalse(TreeChopUtils.isTool(player));
    }
}
