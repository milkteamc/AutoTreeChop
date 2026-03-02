package org.milkteamc.autotreechop.utils;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.milkteamc.autotreechop.Config;

/**
 * Refactored BlockDiscoveryUtils - All methods work with BlockSnapshot
 * Safe to call from async threads
 */
public class BlockDiscoveryUtils {

    /**
     * Discover tree blocks from snapshot (ASYNC-SAFE)
     *
     * @param snapshot Block snapshot captured synchronously
     * @param startLocation Starting location
     * @param config Plugin configuration
     * @param connectedOnly Whether to only follow connected blocks
     * @param maxBlocks Maximum blocks to discover
     * @return Set of locations that are part of the tree
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

            // Check if it's a log
            if (!isLog(type, config)) {
                continue;
            }

            // Check same type if required
            if (config.isStopChoppingIfDifferentTypes() && type != originalType) {
                continue;
            }

            // Add to tree blocks
            treeBlocks.add(currentKey.toLocation(world));

            // Add neighbors to queue
            addNeighborsToQueue(currentKey, queue, visited, snapshot, connectedOnly);
        }

        return treeBlocks;
    }

    /**
     * Discover leaves from snapshot (ASYNC-SAFE)
     *
     * @param snapshot Block snapshot captured synchronously
     * @param centerLocation Center of search area
     * @param radius Search radius
     * @param config Plugin configuration
     * @param removedLogs Locations of logs that will be removed
     * @return Set of leaf blocks that should be removed
     */
    public static Set<Location> discoverLeavesBFS(
            BlockSnapshot snapshot, Location centerLocation, int radius, Config config, Set<Location> removedLogs) {

        Set<Location> leaves = new HashSet<>();
        Set<BlockSnapshot.LocationKey> visited = new HashSet<>();
        Queue<BlockSnapshot.LocationKey> queue = new LinkedList<>();

        World world = snapshot.getWorld();
        BlockSnapshot.LocationKey centerKey = new BlockSnapshot.LocationKey(centerLocation);
        int radiusSquared = radius * radius;

        queue.add(centerKey);
        visited.add(centerKey);

        String mode = config.getLeafRemovalMode().toLowerCase();

        // Convert removedLogs to LocationKey set for fast lookup
        Set<BlockSnapshot.LocationKey> removedLogKeys = new HashSet<>();
        for (Location loc : removedLogs) {
            removedLogKeys.add(new BlockSnapshot.LocationKey(loc));
        }

        while (!queue.isEmpty()) {
            BlockSnapshot.LocationKey currentKey = queue.poll();
            Location loc = currentKey.toLocation(world);

            // Check if within radius (spherical)
            if (getDistanceSquared(currentKey, centerKey) > radiusSquared) {
                continue;
            }

            Material type = snapshot.getBlockType(currentKey.getX(), currentKey.getY(), currentKey.getZ());

            // If it's a leaf, check if should be removed
            if (isLeafBlock(type, config)) {
                boolean shouldRemove = shouldRemoveLeaf(snapshot, currentKey, mode, config, removedLogKeys);
                if (shouldRemove) {
                    leaves.add(loc);
                }

                // Continue searching through leaves
                addLeafNeighborsToQueue(currentKey, queue, visited, centerKey, radiusSquared);
            }
            // If it's a log, continue searching (but don't remove)
            else if (isLog(type, config)) {
                addLeafNeighborsToQueue(currentKey, queue, visited, centerKey, radiusSquared);
            }
            // If it's AIR or other blocks, still search neighbors
            // This is CRITICAL for starting from a removed log location
            else {
                addLeafNeighborsToQueue(currentKey, queue, visited, centerKey, radiusSquared);
            }
        }

        return leaves;
    }

    /**
     * Discover leaves using radial scan (ASYNC-SAFE, faster for small areas)
     */
    public static Set<Location> discoverLeavesRadial(
            BlockSnapshot snapshot, Location centerLocation, int radius, Config config, Set<Location> removedLogs) {

        Set<Location> leaves = new HashSet<>();
        World world = snapshot.getWorld();
        BlockSnapshot.LocationKey centerKey = new BlockSnapshot.LocationKey(centerLocation);
        String mode = config.getLeafRemovalMode().toLowerCase();

        // Convert removedLogs to LocationKey set
        Set<BlockSnapshot.LocationKey> removedLogKeys = new HashSet<>();
        for (Location loc : removedLogs) {
            removedLogKeys.add(new BlockSnapshot.LocationKey(loc));
        }

        int radiusSquared = radius * radius;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    // Spherical check
                    if (x * x + y * y + z * z > radiusSquared) {
                        continue;
                    }

                    BlockSnapshot.LocationKey key = new BlockSnapshot.LocationKey(
                            centerKey.getX() + x, centerKey.getY() + y, centerKey.getZ() + z);

                    Material type = snapshot.getBlockType(key.getX(), key.getY(), key.getZ());

                    if (isLeafBlock(type, config)) {
                        boolean shouldRemove = shouldRemoveLeaf(snapshot, key, mode, config, removedLogKeys);
                        if (shouldRemove) {
                            leaves.add(key.toLocation(world));
                        }
                    }
                }
            }
        }

        return leaves;
    }

    // ==================== Private Helper Methods ====================

    private static boolean shouldRemoveLeaf(
            BlockSnapshot snapshot,
            BlockSnapshot.LocationKey leafKey,
            String mode,
            Config config,
            Set<BlockSnapshot.LocationKey> removedLogKeys) {

        switch (mode) {
            case "aggressive":
                return true;

            case "radius":
                return !hasNearbyActiveLog(snapshot, leafKey, config, removedLogKeys, 4);

            case "smart":
            default:
                return isOrphanedLeaf(snapshot, leafKey, config, removedLogKeys);
        }
    }

    private static boolean isOrphanedLeaf(
            BlockSnapshot snapshot,
            BlockSnapshot.LocationKey leafKey,
            Config config,
            Set<BlockSnapshot.LocationKey> removedLogKeys) {

        if (!hasNearbyActiveLog(snapshot, leafKey, config, removedLogKeys, 2)) {
            return true;
        }

        Set<BlockSnapshot.LocationKey> visited = new HashSet<>();
        return !isConnectedToActiveLog(snapshot, leafKey, config, removedLogKeys, visited, 0);
    }

    private static boolean hasNearbyActiveLog(
            BlockSnapshot snapshot,
            BlockSnapshot.LocationKey leafKey,
            Config config,
            Set<BlockSnapshot.LocationKey> removedLogKeys,
            int checkRadius) {

        for (int x = -checkRadius; x <= checkRadius; x++) {
            for (int y = -checkRadius; y <= checkRadius; y++) {
                for (int z = -checkRadius; z <= checkRadius; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    BlockSnapshot.LocationKey checkKey =
                            new BlockSnapshot.LocationKey(leafKey.getX() + x, leafKey.getY() + y, leafKey.getZ() + z);

                    Material type = snapshot.getBlockType(checkKey.getX(), checkKey.getY(), checkKey.getZ());

                    if (isLog(type, config) && !removedLogKeys.contains(checkKey)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isConnectedToActiveLog(
            BlockSnapshot snapshot,
            BlockSnapshot.LocationKey startKey,
            Config config,
            Set<BlockSnapshot.LocationKey> removedLogKeys,
            Set<BlockSnapshot.LocationKey> visited,
            int depth) {

        if (depth > 8 || visited.size() > 100) {
            return false;
        }

        if (visited.contains(startKey)) {
            return false;
        }
        visited.add(startKey);

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    BlockSnapshot.LocationKey checkKey = new BlockSnapshot.LocationKey(
                            startKey.getX() + x, startKey.getY() + y, startKey.getZ() + z);

                    Material type = snapshot.getBlockType(checkKey.getX(), checkKey.getY(), checkKey.getZ());

                    if (isLog(type, config) && !removedLogKeys.contains(checkKey)) {
                        return true;
                    }

                    if (isLeafBlock(type, config) && !visited.contains(checkKey)) {
                        if (isConnectedToActiveLog(snapshot, checkKey, config, removedLogKeys, visited, depth + 1)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
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

                    // Check if block exists in snapshot
                    if (!snapshot.hasBlock(neighborKey.toLocation(snapshot.getWorld()))) {
                        continue;
                    }

                    // Check connectivity if required
                    if (connectedOnly && !isConnectedKeys(current, neighborKey)) {
                        continue;
                    }

                    visited.add(neighborKey);
                    queue.add(neighborKey);
                }
            }
        }
    }

    private static void addLeafNeighborsToQueue(
            BlockSnapshot.LocationKey current,
            Queue<BlockSnapshot.LocationKey> queue,
            Set<BlockSnapshot.LocationKey> visited,
            BlockSnapshot.LocationKey center,
            int radiusSquared) {

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    BlockSnapshot.LocationKey neighborKey =
                            new BlockSnapshot.LocationKey(current.getX() + x, current.getY() + y, current.getZ() + z);

                    if (visited.contains(neighborKey)) continue;
                    if (getDistanceSquared(neighborKey, center) > radiusSquared) continue;

                    // CRITICAL: Only add to queue if not already visited
                    // Don't need to check snapshot here - will be checked when processing
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

    private static int getDistanceSquared(BlockSnapshot.LocationKey k1, BlockSnapshot.LocationKey k2) {
        int dx = k1.getX() - k2.getX();
        int dy = k1.getY() - k2.getY();
        int dz = k1.getZ() - k2.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    public static boolean isLog(Material material, Config config) {
        return config.getLogTypes().contains(material);
    }

    public static boolean isLeafBlock(Material material, Config config) {
        return config.getLeafTypes().contains(material);
    }
}
