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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.utils.TreeStructureDetector.Result;

class TreeStructureDetectorTest {
    private final World world = mock(World.class);
    private final Config config = mock(Config.class);
    private final Map<Location, Block> blocks = new HashMap<>();
    private final Set<Location> logs = new HashSet<>();

    @BeforeEach
    void setup() {
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(any(Location.class)))
                .thenAnswer(call -> blocks.computeIfAbsent(call.getArgument(0), location -> {
                    Block block = mock(Block.class);
                    when(block.getType()).thenReturn(Material.AIR);
                    return block;
                }));
        when(config.getLogTypes())
                .thenAnswer(call -> Set.of(
                        Material.OAK_LOG,
                        Material.SPRUCE_LOG,
                        Material.BIRCH_LOG,
                        Material.JUNGLE_LOG,
                        Material.ACACIA_LOG,
                        Material.DARK_OAK_LOG,
                        Material.CHERRY_LOG,
                        Material.MANGROVE_LOG,
                        Material.MANGROVE_ROOTS,
                        Material.MUDDY_MANGROVE_ROOTS,
                        Material.CRIMSON_STEM,
                        Material.WARPED_STEM,
                        Material.STRIPPED_OAK_LOG,
                        Material.OAK_WOOD,
                        Material.WARPED_HYPHAE));
        when(config.getLeafTypes())
                .thenReturn(Set.of(
                        Material.OAK_LEAVES,
                        Material.MANGROVE_LEAVES,
                        Material.NETHER_WART_BLOCK,
                        Material.WARPED_WART_BLOCK));
    }

    private Block put(int x, int y, int z, Material type) {
        Location location = new Location(world, x, y, z);
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        blocks.put(location, block);
        return block;
    }

    private void log(int x, int y, int z, Material type) {
        put(x, y, z, type);
        logs.add(new Location(world, x, y, z));
    }

    private void tree(Material type) {
        for (int y = 64; y < 69; y++) log(0, y, 0, type);
        put(0, 63, 0, Material.DIRT);
        put(0, 69, 0, Material.OAK_LEAVES);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "OAK_LOG",
                "SPRUCE_LOG",
                "BIRCH_LOG",
                "JUNGLE_LOG",
                "ACACIA_LOG",
                "DARK_OAK_LOG",
                "CHERRY_LOG",
                "MANGROVE_LOG",
                "CRIMSON_STEM",
                "WARPED_STEM"
            })
    void ordinaryTrunksAreNotSuspicious(String material) {
        tree(Material.valueOf(material));
        assertEquals(Result.NO_EVIDENCE, TreeStructureDetector.inspect(logs, config));
    }

    @Test
    void largeTrunksBranchesAndRootsAreNotMistakenForFlatBuildings() {
        for (int y = 64; y < 72; y++)
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) log(x, y, z, Material.SPRUCE_LOG);
        for (int x = 2; x <= 7; x++) log(x, 70, 0, Material.SPRUCE_LOG);
        log(-1, 64, 0, Material.MANGROVE_ROOTS);
        log(-2, 63, 0, Material.MUDDY_MANGROVE_ROOTS);
        put(-2, 62, 0, Material.STONE);
        assertEquals(Result.NO_EVIDENCE, TreeStructureDetector.inspect(logs, config));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "OAK_PLANKS",
                "STONE_BRICKS",
                "OAK_STAIRS",
                "STONE_SLAB",
                "GLASS_PANE",
                "OAK_DOOR",
                "OAK_TRAPDOOR",
                "OAK_FENCE",
                "OAK_WALL_SIGN",
                "OAK_WALL_HANGING_SIGN",
                "CHEST"
            })
    void attachedConstructionTriggersEvenWhenNaturalLeavesArePresent(String material) {
        tree(Material.OAK_LOG);
        put(1, 65, 0, Material.valueOf(material));
        assertEquals(Result.SUSPICIOUS, TreeStructureDetector.inspect(logs, config));
    }

    @Test
    void nearbyButUnattachedBuildingsDoNotTrigger() {
        tree(Material.OAK_LOG);
        put(2, 65, 0, Material.OAK_PLANKS);
        assertEquals(Result.NO_EVIDENCE, TreeStructureDetector.inspect(logs, config));
    }

    @Test
    void logReplacedByConstructionAfterDiscoveryIsStillDetected() {
        tree(Material.OAK_LOG);
        put(0, 66, 0, Material.OAK_PLANKS);
        assertEquals(Result.SUSPICIOUS, TreeStructureDetector.inspect(logs, config));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STRIPPED_OAK_LOG", "OAK_WOOD", "WARPED_HYPHAE"})
    void configuredProcessedWoodRequiresConfirmation(String material) {
        tree(Material.valueOf(material));
        assertEquals(Result.SUSPICIOUS, TreeStructureDetector.inspect(logs, config));
    }

    @Test
    void onlyDecorativeFoliageIsSuspiciousButMixedNaturalCanopyIsNot() {
        tree(Material.OAK_LOG);
        Leaves decoration = mock(Leaves.class);
        when(decoration.isPersistent()).thenReturn(true);
        when(put(0, 69, 0, Material.OAK_LEAVES).getBlockData()).thenReturn(decoration);
        assertEquals(Result.SUSPICIOUS, TreeStructureDetector.inspect(logs, config));
        Leaves natural = mock(Leaves.class);
        when(put(1, 68, 0, Material.OAK_LEAVES).getBlockData()).thenReturn(natural);
        assertEquals(Result.NO_EVIDENCE, TreeStructureDetector.inspect(logs, config));
    }

    @Test
    void flatLongLogBeamRequiresConfirmation() {
        for (int x = 0; x < 5; x++) log(x, 64, 0, Material.OAK_LOG);
        put(0, 65, 0, Material.OAK_LEAVES);
        assertEquals(Result.SUSPICIOUS, TreeStructureDetector.inspect(logs, config));
    }

    @Test
    void scanDoesNotLoadUnloadedChunks() {
        log(15, 64, 0, Material.OAK_LOG);
        when(world.isChunkLoaded(1, 0)).thenReturn(false);
        assertEquals(Result.INCOMPLETE, TreeStructureDetector.inspect(logs, config));
        verify(world, never()).getBlockAt(new Location(world, 16, 64, 0));
    }

    @Test
    void scanDoesNotReadAnotherFoliaRegion() {
        log(15, 64, 0, Material.OAK_LOG);
        Location outside = new Location(world, 16, 64, 0);
        try (var access = mockStatic(RegionAccess.class)) {
            access.when(() -> RegionAccess.owns(any(Location.class))).thenReturn(true);
            access.when(() -> RegionAccess.owns(outside)).thenReturn(false);
            assertEquals(Result.INCOMPLETE, TreeStructureDetector.inspect(logs, config));
            verify(world, never()).getBlockAt(outside);
        }
    }
}
