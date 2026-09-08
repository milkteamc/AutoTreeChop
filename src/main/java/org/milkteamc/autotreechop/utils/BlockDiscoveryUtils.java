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

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.milkteamc.autotreechop.Config;

/** Discovers connected tree blocks and canopy from immutable snapshots. */
public class BlockDiscoveryUtils {

    // ── Tree discovery ────────────────────────────────────────────────────────

    /**
     * BFS discovery of tree log blocks from a snapshot (async-safe).
     *
     * @param snapshot      block snapshot captured synchronously
     * @param startLocation starting location
     * @param config        plugin configuration
     * @param connectedOnly whether to only follow face-connected (non-diagonal) blocks
     * @param maxBlocks     maximum blocks to discover
     * @return set of locations that are part of the tree
     */
    public static Set<Location> discoverTreeBFS(
            BlockSnapshot snapshot, Location startLocation, Config config, boolean connectedOnly, int maxBlocks) {

        Set<Location> treeBlocks = new HashSet<>();
        Queue<BlockSnapshot.LocationKey> queue = new LinkedList<>();
        Set<BlockSnapshot.LocationKey> visited = new HashSet<>();

        Material originalType = snapshot.getBlockType(startLocation);
        World world = snapshot.getWorld();

        BlockSnapshot.LocationKey startKey = new BlockSnapshot.LocationKey(startLocation);
        queue.add(startKey);
        visited.add(startKey);

        while (!queue.isEmpty() && treeBlocks.size() <= maxBlocks) {
            BlockSnapshot.LocationKey currentKey = queue.poll();
            Material type = snapshot.getBlockType(currentKey.getX(), currentKey.getY(), currentKey.getZ());

            if (!isLog(type, config)) {
                continue;
            }

            if (config.isStopChoppingIfDifferentTypes() && treeFamily(type) != treeFamily(originalType)) {
                continue;
            }

            treeBlocks.add(currentKey.toLocation(world));

            addNeighborsToQueue(currentKey, queue, visited, snapshot, connectedOnly);
        }

        return treeBlocks;
    }

    /**
     * Multi-source canopy ownership: remove leaves reached from chopped logs only when
     * no surviving log supports them at an equal or shorter leaf-path distance.
     * Shared-canopy ties are preserved. Radius is measured from every chopped log/root.
     */
    public static Set<Location> discoverLeavesBFS(
            BlockSnapshot snapshot, Location centerLocation, int radius, Config config, Set<Location> removedLogs) {
        Set<BlockSnapshot.LocationKey> removed = toLocationKeySet(removedLogs);
        Map<BlockSnapshot.LocationKey, Integer> choppedDistances = leafDistances(snapshot, removed, radius, config);
        Set<BlockSnapshot.LocationKey> surviving = new HashSet<>();
        for (BlockSnapshot.LocationKey key : snapshot.getAllLocations()) {
            if (!removed.contains(key) && isLog(snapshot.getBlockType(key.getX(), key.getY(), key.getZ()), config)) {
                surviving.add(key);
            }
        }
        Map<BlockSnapshot.LocationKey, Integer> survivingDistances = leafDistances(snapshot, surviving, radius, config);
        Set<Location> leaves = new HashSet<>();
        choppedDistances.forEach((key, distance) -> {
            if (distance > 0 && survivingDistances.getOrDefault(key, Integer.MAX_VALUE) > distance) {
                leaves.add(key.toLocation(snapshot.getWorld()));
            }
        });
        return leaves;
    }

    /** Radius/aggressive modes share whole-tree coverage with smart mode. */
    public static Set<Location> discoverLeavesRadial(
            BlockSnapshot snapshot, Location centerLocation, int radius, Config config, Set<Location> removedLogs) {
        Set<BlockSnapshot.LocationKey> removed = toLocationKeySet(removedLogs);
        Map<BlockSnapshot.LocationKey, Integer> candidates = leafDistances(snapshot, removed, radius, config);
        Set<BlockSnapshot.LocationKey> surviving = new HashSet<>();
        for (BlockSnapshot.LocationKey key : snapshot.getAllLocations()) {
            if (!removed.contains(key) && isLog(snapshot.getBlockType(key.getX(), key.getY(), key.getZ()), config)) {
                surviving.add(key);
            }
        }
        boolean protectNearby = !"aggressive".equalsIgnoreCase(config.getLeafRemovalMode());
        Set<Location> leaves = new HashSet<>();
        candidates.forEach((key, distance) -> {
            if (distance > 0 && (!protectNearby || !hasNearbyActiveLogInSet(key, surviving, 4))) {
                leaves.add(key.toLocation(snapshot.getWorld()));
            }
        });
        return leaves;
    }

    private static Map<BlockSnapshot.LocationKey, Integer> leafDistances(
            BlockSnapshot snapshot, Set<BlockSnapshot.LocationKey> sources, int radius, Config config) {
        Map<BlockSnapshot.LocationKey, Integer> distance = new HashMap<>();
        Queue<BlockSnapshot.LocationKey> queue = new ArrayDeque<>();
        for (BlockSnapshot.LocationKey source : sources) {
            distance.put(source, 0);
            queue.add(source);
        }
        while (!queue.isEmpty()) {
            BlockSnapshot.LocationKey current = queue.remove();
            int nextDistance = distance.get(current) + 1;
            if (nextDistance > radius) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockSnapshot.LocationKey next = new BlockSnapshot.LocationKey(
                                current.getX() + dx, current.getY() + dy, current.getZ() + dz);
                        if (distance.containsKey(next)
                                || !isLeafBlock(snapshot.getBlockType(next.getX(), next.getY(), next.getZ()), config))
                            continue;
                        distance.put(next, nextDistance);
                        queue.add(next);
                    }
                }
            }
        }
        return distance;
    }

    private static boolean hasNearbyActiveLogInSet(
            BlockSnapshot.LocationKey leafKey, Set<BlockSnapshot.LocationKey> activeLogSet, int checkRadius) {

        for (int dx = -checkRadius; dx <= checkRadius; dx++) {
            for (int dy = -checkRadius; dy <= checkRadius; dy++) {
                for (int dz = -checkRadius; dz <= checkRadius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (activeLogSet.contains(new BlockSnapshot.LocationKey(
                            leafKey.getX() + dx, leafKey.getY() + dy, leafKey.getZ() + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Converts a {@code Set<Location>} to a {@code Set<BlockSnapshot.LocationKey>} in one pass. */
    private static Set<BlockSnapshot.LocationKey> toLocationKeySet(Set<Location> locations) {
        Set<BlockSnapshot.LocationKey> result = new HashSet<>(locations.size() * 2);
        for (Location loc : locations) {
            result.add(new BlockSnapshot.LocationKey(loc));
        }
        return result;
    }

    private static void addNeighborsToQueue(
            BlockSnapshot.LocationKey current,
            Queue<BlockSnapshot.LocationKey> queue,
            Set<BlockSnapshot.LocationKey> visited,
            BlockSnapshot snapshot,
            boolean connectedOnly) {

        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    BlockSnapshot.LocationKey neighborKey =
                            new BlockSnapshot.LocationKey(current.getX() + x, current.getY() + y, current.getZ() + z);

                    if (visited.contains(neighborKey)) continue;

                    // NOTE: the original hasBlock(neighborKey.toLocation(world)) call created one
                    // Location object per neighbour (up to 26 per queued node).  We skip that
                    // allocation here: if the coordinates are outside the snapshot region,
                    // getBlockType() returns a non-log material and the !isLog() guard in
                    // discoverTreeBFS drops the node naturally.
                    // TODO: add BlockSnapshot.hasBlock(int, int, int) to make the bounds check
                    //       explicit without object allocation.

                    if (connectedOnly && !isConnectedKeys(current, neighborKey)) {
                        continue;
                    }

                    visited.add(neighborKey);
                    queue.add(neighborKey);
                }
            }
        }
    }

    private static boolean isConnectedKeys(BlockSnapshot.LocationKey k1, BlockSnapshot.LocationKey k2) {
        int dx = Math.abs(k1.getX() - k2.getX());
        int dy = Math.abs(k1.getY() - k2.getY());
        int dz = Math.abs(k1.getZ() - k2.getZ());
        return (dx + dy + dz) == 1;
    }

    // ── Public accessors ──────────────────────────────────────────────────────

    /** Roots and mangrove logs are one species even with same-type-only chopping enabled. */
    public static Material treeFamily(Material type) {
        if (type.name().equals("MANGROVE_ROOTS") || type.name().equals("MUDDY_MANGROVE_ROOTS"))
            return Material.valueOf("MANGROVE_LOG");
        return type;
    }

    public static boolean isLog(Material material, Config config) {
        return config.getLogTypes().contains(material);
    }

    public static boolean isLeafBlock(Material material, Config config) {
        return config.getLeafTypes().contains(material);
    }
}
