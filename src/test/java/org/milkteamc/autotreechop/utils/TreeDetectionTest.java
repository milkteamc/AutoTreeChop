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

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.milkteamc.autotreechop.Config;

class TreeDetectionTest {
    private final World world = mock(World.class);
    private final Config config = mock(Config.class);
    private final Map<BlockSnapshot.LocationKey, Material> materials = new HashMap<>();
    private final Map<BlockSnapshot.LocationKey, Block> blocks = new HashMap<>();

    @BeforeEach
    void setup() {
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getBlockAt(any(Location.class))).thenAnswer(call -> block((Location) call.getArgument(0)));
        when(config.getLogTypes())
                .thenReturn(Set.of(
                        Material.OAK_LOG,
                        Material.MANGROVE_LOG,
                        Material.MANGROVE_ROOTS,
                        Material.MUDDY_MANGROVE_ROOTS));
        when(config.getLeafTypes())
                .thenReturn(Set.of(
                        Material.OAK_LEAVES,
                        Material.MANGROVE_LEAVES,
                        Material.AZALEA_LEAVES,
                        Material.FLOWERING_AZALEA_LEAVES));
        when(config.getMaxDiscoveryBlocks()).thenReturn(1000);
        when(config.getLeafRemovalMode()).thenReturn("smart");
    }

    private Block block(Location location) {
        var key = new BlockSnapshot.LocationKey(location);
        return blocks.computeIfAbsent(key, ignored -> {
            Block block = mock(Block.class);
            when(block.getLocation()).thenReturn(location);
            when(block.getWorld()).thenReturn(world);
            when(block.getX()).thenReturn(location.getBlockX());
            when(block.getY()).thenReturn(location.getBlockY());
            when(block.getZ()).thenReturn(location.getBlockZ());
            when(block.getType()).thenAnswer(call -> materials.getOrDefault(key, Material.AIR));
            when(block.getRelative(anyInt(), anyInt(), anyInt()))
                    .thenAnswer(call -> block(location.clone()
                            .add((int) call.getArgument(0), (int) call.getArgument(1), (int) call.getArgument(2))));
            return block;
        });
    }

    private Location put(int x, int y, int z, Material type) {
        materials.put(new BlockSnapshot.LocationKey(x, y, z), type);
        return new Location(world, x, y, z);
    }

    @Test
    void tallTreeAndBranchCanopiesAreCapturedFromEveryLog() {
        Set<Location> logs = new HashSet<>();
        for (int y = 64; y <= 94; y++) logs.add(put(0, y, 0, Material.OAK_LOG));
        for (int x = 1; x <= 12; x++) logs.add(put(x, 90, 0, Material.OAK_LOG));
        Set<Location> expectedLeaves = Set.of(
                put(0, 95, 0, Material.OAK_LEAVES),
                put(12, 91, 0, Material.OAK_LEAVES),
                put(13, 92, 0, Material.OAK_LEAVES));
        BlockSnapshot tree =
                BlockSnapshotCreator.captureTreeRegion(block(new Location(world, 0, 64, 0)), config, false, 1000);
        assertTrue(tree.isComplete());
        assertEquals(
                logs, BlockDiscoveryUtils.discoverTreeBFS(tree, new Location(world, 0, 64, 0), config, false, 500));
        BlockSnapshot canopy = BlockSnapshotCreator.captureLeafRegion(logs, 10, config);
        assertNotNull(canopy);
        assertEquals(
                expectedLeaves,
                BlockDiscoveryUtils.discoverLeavesBFS(canopy, new Location(world, 0, 64, 0), 10, config, logs));
    }

    @Test
    void mangroveRootsReachTrunkAndCrownEvenWithSameTypeRestriction() {
        when(config.isStopChoppingIfDifferentTypes()).thenReturn(true);
        Location base = put(0, 64, 0, Material.MUDDY_MANGROVE_ROOTS);
        Location root = put(0, 65, 0, Material.MANGROVE_ROOTS);
        Location trunk = put(0, 66, 0, Material.MANGROVE_LOG);
        Location crown = put(0, 67, 0, Material.MANGROVE_LEAVES);
        Set<Location> expected = Set.of(base, root, trunk);
        BlockSnapshot tree = BlockSnapshotCreator.captureTreeRegion(block(base), config, true, 1000);
        assertTrue(tree.isComplete());
        assertEquals(expected, BlockDiscoveryUtils.discoverTreeBFS(tree, base, config, true, 500));
        assertEquals(Material.MANGROVE_LOG, BlockDiscoveryUtils.treeFamily(Material.MUDDY_MANGROVE_ROOTS));
        BlockSnapshot canopy = BlockSnapshotCreator.captureLeafRegion(expected, 10, config);
        assertEquals(Set.of(crown), BlockDiscoveryUtils.discoverLeavesBFS(canopy, base, 10, config, expected));
    }

    @Test
    void neighboringTreeSupportAndSharedCanopyArePreserved() {
        Location removed = put(0, 64, 0, Material.OAK_LOG);
        put(4, 64, 0, Material.OAK_LOG);
        Location ownLeaf = put(1, 64, 0, Material.OAK_LEAVES);
        put(2, 64, 0, Material.OAK_LEAVES);
        put(3, 64, 0, Material.OAK_LEAVES);
        BlockSnapshot canopy = BlockSnapshotCreator.captureLeafRegion(Set.of(removed), 10, config);
        assertEquals(
                Set.of(ownLeaf), BlockDiscoveryUtils.discoverLeavesBFS(canopy, removed, 10, config, Set.of(removed)));
    }

    @Test
    void discoveryLimitDoesNotReturnAnApparentlyCompletePartialTree() {
        Location base = put(0, 64, 0, Material.OAK_LOG);
        put(0, 65, 0, Material.OAK_LOG);
        assertFalse(BlockSnapshotCreator.captureTreeRegion(block(base), config, true, 1)
                .isComplete());
        assertTrue(BlockSnapshotCreator.captureTreeRegion(block(base), config, true, 2)
                .isComplete());
    }

    @Test
    void azaleaAndFloweringAzaleaLeavesAreDiscovered() {
        Location base = put(0, 64, 0, Material.OAK_LOG);
        Location leaf = put(0, 65, 0, Material.AZALEA_LEAVES);
        Location flowering = put(0, 66, 0, Material.FLOWERING_AZALEA_LEAVES);
        BlockSnapshot canopy = BlockSnapshotCreator.captureLeafRegion(Set.of(base), 10, config);
        assertEquals(
                Set.of(leaf, flowering), BlockDiscoveryUtils.discoverLeavesBFS(canopy, base, 10, config, Set.of(base)));
    }
}
