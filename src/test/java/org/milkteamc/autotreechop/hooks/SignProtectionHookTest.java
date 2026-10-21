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
 
package org.milkteamc.autotreechop.hooks;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.junit.jupiter.api.Test;

class SignProtectionHookTest {
    @Test
    void recognizesSignsOnlyWhenTheLogSupportsThem() {
        Block wallSign = mock(Block.class);
        Directional data = mock(Directional.class);
        when(wallSign.getType()).thenReturn(Material.OAK_WALL_SIGN);
        when(wallSign.getBlockData()).thenReturn(data);
        when(data.getFacing()).thenReturn(BlockFace.EAST);
        assertTrue(SignProtectionHook.supportedSign(wallSign, BlockFace.EAST));
        assertFalse(SignProtectionHook.supportedSign(wallSign, BlockFace.WEST));

        Block standingSign = mock(Block.class);
        when(standingSign.getType()).thenReturn(Material.OAK_SIGN);
        assertTrue(SignProtectionHook.supportedSign(standingSign, BlockFace.UP));
        assertFalse(SignProtectionHook.supportedSign(standingSign, BlockFace.NORTH));

        Block hangingSign = mock(Block.class);
        when(hangingSign.getType()).thenReturn(Material.OAK_HANGING_SIGN);
        assertTrue(SignProtectionHook.supportedSign(hangingSign, BlockFace.DOWN));
        assertFalse(SignProtectionHook.supportedSign(hangingSign, BlockFace.UP));
    }

    @Test
    void protectedAttachedSignBlocksChopButUnrelatedSignDoesNot() {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Block log = mock(Block.class);
        Block sign = mock(Block.class);
        Block air = mock(Block.class);
        Directional signData = mock(Directional.class);
        when(log.getType()).thenReturn(Material.OAK_LOG);
        when(sign.getType()).thenReturn(Material.OAK_WALL_SIGN);
        when(sign.getBlockData()).thenReturn(signData);
        when(signData.getFacing()).thenReturn(BlockFace.EAST);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(any(Location.class))).thenAnswer(invocation -> {
            Location location = invocation.getArgument(0);
            if (location.getBlockX() == 0 && location.getBlockY() == 64 && location.getBlockZ() == 0) return log;
            if (location.getBlockX() == 1 && location.getBlockY() == 64 && location.getBlockZ() == 0) return sign;
            return air;
        });
        SignProtectionHook hook = new SignProtectionHook(block -> false, block -> block == sign);
        Location location = new Location(world, 0, 64, 0);
        assertEquals(SignProtectionHook.Result.PROTECTED, hook.inspect(location));

        when(signData.getFacing()).thenReturn(BlockFace.WEST);
        assertEquals(SignProtectionHook.Result.SAFE, hook.inspect(location));
    }

    @Test
    void unloadedNeighborPreventsUnsafeChop() {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt()))
                .thenAnswer(invocation ->
                        invocation.<Integer>getArgument(0) == 0 && invocation.<Integer>getArgument(1) == 0);
        Block log = mock(Block.class);
        when(log.getType()).thenReturn(Material.OAK_LOG);
        when(world.getBlockAt(any(Location.class))).thenReturn(log);
        SignProtectionHook hook = new SignProtectionHook(block -> false, block -> false);
        assertEquals(SignProtectionHook.Result.INCOMPLETE, hook.inspect(new Location(world, 0, 64, 0)));
    }
}
