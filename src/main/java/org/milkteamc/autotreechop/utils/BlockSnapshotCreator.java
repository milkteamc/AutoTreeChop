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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.milkteamc.autotreechop.Config;

/**
 * Creates block snapshots synchronously for async processing
 * MUST be called on the region/main thread
 */
public class BlockSnapshotCreator {

    /**
     * Dynamically scan and capture tree structure
     * This is a lightweight BFS that only records locations and types
     *
     * @param startBlock The starting block (must be a log)
     * @param config Plugin configuration
     * @param connectedOnly Whether to only follow connected blocks
     * @param maxBlocks Maximum blocks to scan
     * @return BlockSnapshot containing all scanned block data
     */
    public static BlockSnapshot captureTreeRegion(
            Block startBlock, Config config, boolean connectedOnly, int maxBlocks) {

        Map<BlockSnapshot.LocationKey, Material> blockData = new HashMap<>();
        Queue<Block> queue = new LinkedList<>();
        Set<BlockSnapshot.LocationKey> visited = new HashSet<>();

        Material originalType = startBlock.getType();
        Location center = startBlock.getLocation();

        queue.add(startBlock);
        visited.add(new BlockSnapshot.LocationKey(center));

        // Quick BFS to find all connected logs
        while (!queue.isEmpty()) {
            Block current = queue.poll();
            BlockSnapshot.LocationKey key = new BlockSnapshot.LocationKey(current.getLocation());
            if (!RegionAccess.owns(current.getLocation())) {
                return new BlockSnapshot(blockData, startBlock.getWorld(), center, false);
            }
            Material type = current.getType();

            // Only continue if it's a log
            if (!isLog(type, config)) {
                continue;
            }

            // Check same type if required
            if (config.isStopChoppingIfDifferentTypes()
                    && BlockDiscoveryUtils.treeFamily(type) != BlockDiscoveryUtils.treeFamily(originalType)) {
                continue;
            }

            // Count tree blocks, not the surrounding air, against the discovery budget.
            if (blockData.size() >= maxBlocks)
                return new BlockSnapshot(blockData, startBlock.getWorld(), center, false);
            blockData.put(key, type);

            // Add neighbors
            for (int y = -1; y <= 1; y++) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;

                        Block neighbor = current.getRelative(x, y, z);
                        BlockSnapshot.LocationKey neighborKey = new BlockSnapshot.LocationKey(neighbor.getLocation());

                        if (visited.contains(neighborKey)) continue;

                        // Check connectivity if required
                        if (connectedOnly && !isConnected(current, neighbor)) {
                            continue;
                        }

                        visited.add(neighborKey);
                        queue.add(neighbor);
                    }
                }
            }
        }

        return new BlockSnapshot(blockData, startBlock.getWorld(), center, queue.isEmpty());
    }

    /** Legacy entry point. New chopping jobs seed capture from all discovered logs. */
    public static BlockSnapshot captureLeafRegion(Block centerBlock, int radius, Config config) {
        return captureLeafRegion(Set.of(centerBlock.getLocation()), radius, config);
    }

    /**
     * Walk connected canopy from every tree block, so height and branch spread cannot
     * truncate coverage. Also capture supporting logs and a leaf buffer for neighboring trees.
     * Return null when ownership/work limits prevent a complete safe snapshot.
     */
    public static BlockSnapshot captureLeafRegion(Set<Location> logs, int radius, Config config) {
        if (logs.isEmpty()) return null;
        Location center = logs.iterator().next();
        var world = center.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        Map<BlockSnapshot.LocationKey, Material> data = new HashMap<>();
        Map<BlockSnapshot.LocationKey, Integer> visited = new HashMap<>();
        Queue<BlockSnapshot.LocationKey> queue = new LinkedList<>();
        for (Location log : logs) {
            if (!RegionAccess.owns(log)) return null;
            var key = new BlockSnapshot.LocationKey(log);
            data.put(key, log.getBlock().getType());
            visited.put(key, 0);
            queue.add(key);
        }
        // Bound pathological/custom canopies without deleting an incomplete selection.
        long budget = Math.max(10000L, (long) config.getMaxDiscoveryBlocks() * 100);
        while (!queue.isEmpty()) {
            var current = queue.remove();
            int distance = visited.get(current) + 1;
            if (distance > (long) radius + 4) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        var key = new BlockSnapshot.LocationKey(
                                current.getX() + dx, current.getY() + dy, current.getZ() + dz);
                        if (key.getY() < minY || key.getY() >= maxY || visited.containsKey(key)) continue;
                        if (visited.size() >= budget) return null;
                        visited.put(key, distance);
                        Location location = key.toLocation(world);
                        if (!RegionAccess.owns(location)) return null;
                        Material type = location.getBlock().getType();
                        if (isLeafBlock(type, config)) {
                            data.put(key, type);
                            queue.add(key);
                        } else if (isLog(type, config)) {
                            data.put(key, type);
                        }
                    }
                }
            }
        }
        return new BlockSnapshot(data, world, center);
    }

    /**
     * Check if two blocks are connected (face-adjacent only)
     */
    private static boolean isConnected(Block b1, Block b2) {
        int dx = Math.abs(b1.getX() - b2.getX());
        int dy = Math.abs(b1.getY() - b2.getY());
        int dz = Math.abs(b1.getZ() - b2.getZ());
        return (dx + dy + dz) == 1;
    }

    private static boolean isLog(Material material, Config config) {
        return config.getLogTypes().contains(material);
    }

    private static boolean isLeafBlock(Material material, Config config) {
        return config.getLeafTypes().contains(material);
    }
}
