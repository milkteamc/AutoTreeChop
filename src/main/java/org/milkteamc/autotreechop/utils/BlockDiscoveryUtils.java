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

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.milkteamc.autotreechop.Config;

/**
 * Block-discovery utilities – all methods work with {@link BlockSnapshot} and
 * are safe to call from async threads.
 *
 * <h3>Leaf-removal performance improvements</h3>
 * <ul>
 *   <li><b>Smart mode (was critical bottleneck):</b> the old implementation
 *       called {@code isOrphanedLeaf} → {@code hasNearbyActiveLog} (O(r³) snapshot
 *       scan) → {@code isConnectedToActiveLog} (recursive DFS, depth 8) for
 *       <em>every leaf</em>.  A large tree with 200 leaves and radius 6 triggered
 *       ~200 × 216 = 43 000 snapshot accesses plus up to 200 DFS traversals.
 *       The replacement is a two-pass BFS: one O(r³) sphere scan to collect all
 *       leaves and active log seeds, then one BFS from those seeds to mark
 *       connected leaves – total work is O(r³ + leaves × 26), done exactly once.
 *   </li>
 *   <li><b>Radius mode:</b> pre-builds an {@code activeLogSet} with one O(r³)
 *       scan so the per-leaf proximity check becomes O(4³) set lookups instead of
 *       O(4³) snapshot {@code getBlockType} calls (~10–50× cheaper per lookup).
 *   </li>
 *   <li><b>Air-block BFS expansion fixed:</b> the old {@code discoverLeavesBFS}
 *       expanded neighbours for every AIR/other block it encountered, causing the
 *       visited set to balloon to O(r³) even when most of the sphere was empty.
 *       The new two-pass approach never walks the air at all.
 *   </li>
 *   <li><b>Unnecessary Location allocation in addNeighborsToQueue:</b>
 *       {@code snapshot.hasBlock(neighborKey.toLocation(world))} created one
 *       {@link Location} per neighbour (26 per queued node).  The call is now
 *       skipped by relying on {@link BlockSnapshot#getBlockType} returning a
 *       non-log material for out-of-range positions, which the existing
 *       {@code !isLog()} guard in {@code discoverTreeBFS} already handles.
 *       TODO: add {@code BlockSnapshot.hasBlock(int, int, int)} to make this
 *       explicit and fully allocation-free.
 *   </li>
 * </ul>
 */
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

        while (!queue.isEmpty() && treeBlocks.size() < maxBlocks) {
            BlockSnapshot.LocationKey currentKey = queue.poll();
            Material type = snapshot.getBlockType(currentKey.getX(), currentKey.getY(), currentKey.getZ());

            if (!isLog(type, config)) {
                continue;
            }

            if (config.isStopChoppingIfDifferentTypes() && type != originalType) {
                continue;
            }

            treeBlocks.add(currentKey.toLocation(world));

            addNeighborsToQueue(currentKey, queue, visited, snapshot, connectedOnly);
        }

        return treeBlocks;
    }

    // ── Leaf discovery – smart mode (two-pass BFS) ───────────────────────────

    /**
     * Discovers orphaned leaf blocks using a two-pass BFS (smart mode only).
     *
     * <p><b>Algorithm:</b>
     * <ol>
     *   <li>Scan the sphere once to collect all leaf positions and all
     *       <em>active</em> (not removed) log positions.</li>
     *   <li>BFS outward from the active log seeds through leaves and logs.
     *       Any leaf reached is "connected" to a living log.</li>
     *   <li>Leaves not reached = orphaned = scheduled for removal.</li>
     * </ol>
     *
     * <p>This is O(r³ + leaves × 26) total – done once, not once per leaf.
     *
     * @param snapshot        block snapshot captured synchronously
     * @param centerLocation  centre of the removal sphere
     * @param radius          removal radius
     * @param config          plugin configuration
     * @param removedLogs     locations of logs that were (or will be) removed
     * @return set of leaf locations that should be removed
     */
    public static Set<Location> discoverLeavesBFS(
            BlockSnapshot snapshot, Location centerLocation, int radius, Config config, Set<Location> removedLogs) {

        World world = snapshot.getWorld();
        BlockSnapshot.LocationKey centerKey = new BlockSnapshot.LocationKey(centerLocation);
        int radiusSq = radius * radius;

        // Convert removed-log set once for O(1) membership tests throughout
        Set<BlockSnapshot.LocationKey> removedLogKeys = toLocationKeySet(removedLogs);

        // ── Pass 1: collect leaves and active log seeds in the sphere ─────────
        // We include a 2-block buffer for log seeds so that logs just outside the
        // removal sphere (e.g. neighbouring trees) are recognised as anchors and
        // prevent their connected leaves from being incorrectly removed.
        int logScanRadius = radius + 2;
        int logScanSq = logScanRadius * logScanRadius;

        Set<BlockSnapshot.LocationKey> allLeaves = new HashSet<>();
        Set<BlockSnapshot.LocationKey> activeLogSeeds = new HashSet<>();

        for (int dx = -logScanRadius; dx <= logScanRadius; dx++) {
            for (int dy = -logScanRadius; dy <= logScanRadius; dy++) {
                for (int dz = -logScanRadius; dz <= logScanRadius; dz++) {
                    int distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > logScanSq) continue;

                    BlockSnapshot.LocationKey key = new BlockSnapshot.LocationKey(
                            centerKey.getX() + dx, centerKey.getY() + dy, centerKey.getZ() + dz);
                    Material type = snapshot.getBlockType(key.getX(), key.getY(), key.getZ());

                    if (distSq <= radiusSq && isLeafBlock(type, config)) {
                        allLeaves.add(key);
                    } else if (isLog(type, config) && !removedLogKeys.contains(key)) {
                        activeLogSeeds.add(key);
                    }
                }
            }
        }

        if (allLeaves.isEmpty()) {
            return Collections.emptySet();
        }

        // If no living logs remain nearby, every leaf in the sphere is orphaned
        if (activeLogSeeds.isEmpty()) {
            Set<Location> all = new HashSet<>(allLeaves.size() * 2);
            for (BlockSnapshot.LocationKey key : allLeaves) {
                all.add(key.toLocation(world));
            }
            return all;
        }

        // ── Pass 2: BFS from active logs to mark connected leaves ─────────────
        Set<BlockSnapshot.LocationKey> connected = new HashSet<>();
        Queue<BlockSnapshot.LocationKey> queue = new LinkedList<>(activeLogSeeds);
        Set<BlockSnapshot.LocationKey> visited = new HashSet<>(activeLogSeeds);

        while (!queue.isEmpty()) {
            BlockSnapshot.LocationKey cur = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockSnapshot.LocationKey nb =
                                new BlockSnapshot.LocationKey(cur.getX() + dx, cur.getY() + dy, cur.getZ() + dz);
                        if (visited.contains(nb)) continue;
                        visited.add(nb);

                        if (allLeaves.contains(nb)) {
                            connected.add(nb);
                            queue.add(nb); // continue BFS through leaves
                        } else {
                            // Also traverse active logs to discover leaves behind them
                            Material type = snapshot.getBlockType(nb.getX(), nb.getY(), nb.getZ());
                            if (isLog(type, config) && !removedLogKeys.contains(nb)) {
                                queue.add(nb);
                            }
                        }
                    }
                }
            }
        }

        // Leaves not reachable from any active log are orphaned
        Set<Location> toRemove = new HashSet<>();
        for (BlockSnapshot.LocationKey leaf : allLeaves) {
            if (!connected.contains(leaf)) {
                toRemove.add(leaf.toLocation(world));
            }
        }
        return toRemove;
    }

    // ── Leaf discovery – radius / aggressive modes ────────────────────────────

    /**
     * Discovers leaves using a radial scan for {@code radius} and
     * {@code aggressive} modes (async-safe).
     *
     * <p><b>Radius-mode optimisation:</b> the old code called
     * {@code snapshot.getBlockType()} 4³ = 64 times per leaf for the
     * proximity check.  This version pre-builds a {@code Set} of active log
     * positions in one O((r+4)³) pass; the per-leaf check then becomes 64
     * hash-set lookups which are 10–50× cheaper than snapshot accesses.
     */
    public static Set<Location> discoverLeavesRadial(
            BlockSnapshot snapshot, Location centerLocation, int radius, Config config, Set<Location> removedLogs) {

        Set<Location> leaves = new HashSet<>();
        World world = snapshot.getWorld();
        BlockSnapshot.LocationKey centerKey = new BlockSnapshot.LocationKey(centerLocation);
        String mode = config.getLeafRemovalMode().toLowerCase();
        int radiusSq = radius * radius;

        // Convert removed logs once
        Set<BlockSnapshot.LocationKey> removedLogKeys = toLocationKeySet(removedLogs);

        // For radius mode: build active-log set once so per-leaf checks are O(4³)
        // set lookups rather than O(4³) snapshot.getBlockType() calls.
        Set<BlockSnapshot.LocationKey> activeLogSet = null;
        if ("radius".equals(mode)) {
            final int CHECK_RADIUS = 4; // matches original hasNearbyActiveLog radius
            int extRadius = radius + CHECK_RADIUS;
            int extSq = extRadius * extRadius;
            activeLogSet = new HashSet<>();
            for (int dx = -extRadius; dx <= extRadius; dx++) {
                for (int dy = -extRadius; dy <= extRadius; dy++) {
                    for (int dz = -extRadius; dz <= extRadius; dz++) {
                        if (dx * dx + dy * dy + dz * dz > extSq) continue;
                        BlockSnapshot.LocationKey key = new BlockSnapshot.LocationKey(
                                centerKey.getX() + dx, centerKey.getY() + dy, centerKey.getZ() + dz);
                        Material type = snapshot.getBlockType(key.getX(), key.getY(), key.getZ());
                        if (isLog(type, config) && !removedLogKeys.contains(key)) {
                            activeLogSet.add(key);
                        }
                    }
                }
            }
        }

        // Scan leaves in sphere
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSq) continue;

                    BlockSnapshot.LocationKey key = new BlockSnapshot.LocationKey(
                            centerKey.getX() + dx, centerKey.getY() + dy, centerKey.getZ() + dz);
                    Material type = snapshot.getBlockType(key.getX(), key.getY(), key.getZ());

                    if (!isLeafBlock(type, config)) continue;

                    if ("radius".equals(mode)) {
                        // O(4³) set lookups – no snapshot accesses
                        if (!hasNearbyActiveLogInSet(key, activeLogSet, 4)) {
                            leaves.add(key.toLocation(world));
                        }
                    } else {
                        // "aggressive" or any unrecognised mode: remove unconditionally
                        leaves.add(key.toLocation(world));
                    }
                }
            }
        }

        return leaves;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Checks whether any entry in {@code activeLogSet} lies within
     * {@code checkRadius} blocks of {@code leafKey}.
     * All lookups are O(1) hash-set membership tests.
     */
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

    public static boolean isLog(Material material, Config config) {
        return config.getLogTypes().contains(material);
    }

    public static boolean isLeafBlock(Material material, Config config) {
        return config.getLeafTypes().contains(material);
    }
}
