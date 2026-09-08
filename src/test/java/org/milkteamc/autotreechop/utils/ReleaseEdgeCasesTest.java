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
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import org.milkteamc.autotreechop.Config;

class ReleaseEdgeCasesTest {
    @Test
    void discoveryMustReportOverflowInsteadOfAcceptingPartialTree() {
        World world = mock(World.class);
        Config config = mock(Config.class);
        when(config.getLogTypes()).thenReturn(Set.of(Material.OAK_LOG));
        Location origin = new Location(world, 0, 64, 0);
        BlockSnapshot snapshot = new BlockSnapshot(
                Map.of(
                        new BlockSnapshot.LocationKey(0, 64, 0), Material.OAK_LOG,
                        new BlockSnapshot.LocationKey(0, 65, 0), Material.OAK_LOG,
                        new BlockSnapshot.LocationKey(0, 66, 0), Material.OAK_LOG),
                world,
                origin);
        assertEquals(
                3,
                BlockDiscoveryUtils.discoverTreeBFS(snapshot, origin, config, true, 2)
                        .size());
    }

    @Test
    void replantMustNotReplaceSurvivingLogs() throws Exception {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.OAK_LOG);
        Method method = TreeReplantUtils.class.getDeclaredMethod("isClearForSapling", Block.class);
        method.setAccessible(true);
        assertFalse((boolean) method.invoke(null, block));
    }

    @Test
    void zeroBatchSizeMustBeRejected() {
        AsyncTaskScheduler scheduler = mock(AsyncTaskScheduler.class);
        BatchProcessor processor = new BatchProcessor(null, scheduler);
        assertThrows(
                IllegalArgumentException.class,
                () -> processor.processBatch(
                        java.util.List.of(new Location(null, 0, 64, 0)), 0, 0, (location, index) -> {}, () -> {}));
    }
}
