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
        when(world.getName()).thenReturn("world");
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
            when(block.getRelative(org.bukkit.block.BlockFace.DOWN))
                    .thenAnswer(call -> block(location.clone().subtract(0, 1, 0)));
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

    @Test
    void floatingTreeCannotUseNearbyOrLowerSoil() {
        Location base = put(0, 70, 0, Material.OAK_LOG);
        put(0, 66, 0, Material.DIRT);
        put(1, 69, 0, Material.DIRT);
        TreeGrounding.Result result = TreeGrounding.inspect(Set.of(base), config);
        assertFalse(result.grounded());
        assertTrue(result.plantableBases().isEmpty());
        put(0, 70, 0, Material.AIR);
        assertNull(TreeReplantUtils.findSuitablePlantLocation(base, config));
    }

    @Test
    void mangroveIsGroundedThroughOffsetRootsRatherThanOnlyTheTrunk() {
        Location trunk = put(0, 67, 0, Material.MANGROVE_LOG);
        Location root = put(1, 66, 0, Material.MANGROVE_ROOTS);
        Location muddy = put(2, 65, 0, Material.MUDDY_MANGROVE_ROOTS);
        put(2, 64, 0, Material.MUD);
        TreeGrounding.Result result = TreeGrounding.inspect(Set.of(trunk, root, muddy), config);
        assertTrue(result.grounded());
        assertEquals(Set.of(muddy), result.plantableBases());
    }

    @Test
    void leavesAndWaterDoNotProvideGroundSupport() {
        Location base = put(0, 64, 0, Material.OAK_LOG);
        for (Material support : Set.of(Material.OAK_LEAVES, Material.WATER, Material.OAK_LOG)) {
            put(0, 63, 0, support);
            assertFalse(TreeGrounding.inspect(Set.of(base), config).grounded(), support.name());
        }
    }

    @Test
    void solidFloorAllowsChoppingButOnlySoilAllowsReplanting() {
        Location base = put(0, 64, 0, Material.OAK_LOG);
        put(0, 63, 0, Material.STONE);
        assertTrue(TreeGrounding.inspect(Set.of(base), config).grounded());
        assertTrue(TreeGrounding.inspect(Set.of(base), config).plantableBases().isEmpty());
        put(0, 63, 0, Material.DIRT);
        assertEquals(Set.of(base), TreeGrounding.inspect(Set.of(base), config).plantableBases());
        put(0, 64, 0, Material.AIR);
        assertEquals(base, TreeReplantUtils.findSuitablePlantLocation(base, config));
        put(0, 64, 0, Material.OAK_LOG);
        assertNull(TreeReplantUtils.findSuitablePlantLocation(base, config));
    }

    @Test
    void twoByTwoReplantUsesOnlyTheOriginalFourRemovedBases() {
        Set<Location> bases = new HashSet<>();
        for (int x = 0; x <= 1; x++)
            for (int z = 0; z <= 1; z++) {
                bases.add(put(x, 64, z, Material.AIR));
                put(x, 63, z, Material.DIRT);
            }
        Location origin = new Location(world, 1, 64, 1);
        assertEquals(new Location(world, 0, 64, 0), TreeReplantUtils.find2x2PlantLocation(origin, config, bases));
        bases.remove(new Location(world, 0, 64, 0));
        assertNull(TreeReplantUtils.find2x2PlantLocation(origin, config, bases));
        bases.add(new Location(world, 0, 64, 0));
        put(0, 64, 0, Material.DARK_OAK_LOG);
        assertNull(TreeReplantUtils.find2x2PlantLocation(origin, config, bases));
    }

    @Test
    void foreignRegionSupportIsUnknownAndNeverRead() {
        Location base = put(0, 64, 0, Material.OAK_LOG);
        try (var regions = mockStatic(RegionAccess.class)) {
            regions.when(() -> RegionAccess.owns(base)).thenReturn(true);
            assertNull(TreeGrounding.inspect(Set.of(base), config));
            verify(world, never()).getBlockAt(any(Location.class));
        }
    }

    @Test
    void partiallyCancelledLargeSpruceDoesNotFallBackToOneSapling() {
        Set<Location> originalBases = new HashSet<>();
        for (int x = 0; x <= 1; x++)
            for (int z = 0; z <= 1; z++) {
                originalBases.add(put(x, 64, z, Material.AIR));
                put(x, 63, z, Material.DIRT);
            }
        Location protectedBase = put(1, 64, 1, Material.SPRUCE_LOG);
        Set<Location> removed = new HashSet<>(originalBases);
        removed.remove(protectedBase);
        var plugin = mock(org.milkteamc.autotreechop.AutoTreeChop.class);
        var data = mock(org.milkteamc.autotreechop.database.DataManager.class);
        var playerData = mock(org.milkteamc.autotreechop.PlayerConfig.class);
        var player = mock(org.bukkit.entity.Player.class);
        var uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        when(plugin.getDataManager()).thenReturn(data);
        when(data.getPlayerConfig(uuid)).thenReturn(playerData);
        when(config.isAutoReplantEnabled()).thenReturn(true);
        when(config.getSaplingForLog(Material.SPRUCE_LOG)).thenReturn(Material.SPRUCE_SAPLING);
        var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(call -> {
                        ((Runnable) call.getArgument(1)).run();
                        return null;
                    })
                    .when(scheduler)
                    .runTaskLater(eq(plugin), any(Runnable.class), anyLong());
            TreeReplantUtils.scheduleReplant(
                    player,
                    block(new Location(world, 0, 64, 0)),
                    Material.SPRUCE_LOG,
                    plugin,
                    config,
                    false,
                    false,
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    removed,
                    originalBases,
                    originalBases);
            verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), anyLong());
        }
        for (Block block : blocks.values()) verify(block, never()).setType(any(Material.class));
    }
}
