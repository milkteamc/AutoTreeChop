package org.milkteamc.autotreechop.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.Config;

import java.util.*;

public class BlockDiscoveryUtils {

    public static Set<Location> discoverTreeBFS(
            Block startBlock,
            Config config,
            boolean connectedOnly,
            Player player,
            ProtectionCheckUtils.ProtectionHooks hooks) {

        Set<Location> treeBlocks = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        Set<Location> visited = new HashSet<>();

        Material originalType = startBlock.getType();
        int maxBlocks = config.getMaxDiscoveryBlocks();

        queue.add(startBlock);
        visited.add(startBlock.getLocation());

        while (!queue.isEmpty() && treeBlocks.size() < maxBlocks) {
            Block current = queue.poll();
            Location loc = current.getLocation();

            // Permission check
            if (!ProtectionCheckUtils.canModifyBlock(player, loc, hooks)) {
                continue;
            }

            // Check if it's a log
            if (!isLog(current.getType(), config)) {
                continue;
            }

            // Check same type if required
            if (config.isStopChoppingIfDifferentTypes() && current.getType() != originalType) {
                continue;
            }

            treeBlocks.add(loc);

            // Add neighbors to queue
            addNeighborsToQueue(current, queue, visited, connectedOnly);
        }

        return treeBlocks;
    }

    public static Set<Block> discoverLeavesBFS(
            Block centerBlock,
            int radius,
            Config config,
            Set<Location> removedLogs) {

        Set<Block> leaves = new HashSet<>();
        Set<Location> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();

        Location center = centerBlock.getLocation();
        int radiusSquared = radius * radius;

        queue.add(centerBlock);
        visited.add(center);

        String mode = config.getLeafRemovalMode().toLowerCase();

        while (!queue.isEmpty()) {
            Block current = queue.poll();
            Location loc = current.getLocation();

            // Check if within radius (spherical)
            if (loc.distanceSquared(center) > radiusSquared) {
                continue;
            }

            Material type = current.getType();

            // If it's a leaf, check if should be removed
            if (isLeafBlock(type, config)) {
                boolean shouldRemove = shouldRemoveLeaf(current, mode, config, removedLogs);
                if (shouldRemove) {
                    leaves.add(current);
                }

                // Continue searching through leaves
                addLeafNeighborsToQueue(current, queue, visited, center, radiusSquared);
            }
            // If it's a log, continue searching (but don't remove)
            else if (isLog(type, config)) {
                addLeafNeighborsToQueue(current, queue, visited, center, radiusSquared);
            }
        }

        return leaves;
    }

    public static Set<Block> discoverLeavesRadial(
            Block centerBlock,
            int radius,
            Config config,
            Set<Location> removedLogs) {

        Set<Block> leaves = new HashSet<>();
        Location center = centerBlock.getLocation();
        String mode = config.getLeafRemovalMode().toLowerCase();

        // Use BitSet for fast visited checks (3D coordinate to 1D index mapping)
        int size = (radius * 2 + 1);
        BitSet visited = new BitSet(size * size * size);

        int radiusSquared = radius * radius;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location loc = center.clone().add(x, y, z);

                    // Spherical check
                    if (loc.distanceSquared(center) > radiusSquared) {
                        continue;
                    }

                    // BitSet index
                    int index = getBitSetIndex(x, y, z, radius, size);
                    if (visited.get(index)) {
                        continue;
                    }
                    visited.set(index);

                    Block block = loc.getBlock();
                    Material type = block.getType();

                    if (isLeafBlock(type, config)) {
                        boolean shouldRemove = shouldRemoveLeaf(block, mode, config, removedLogs);
                        if (shouldRemove) {
                            leaves.add(block);
                        }
                    }
                }
            }
        }

        return leaves;
    }

    private static boolean shouldRemoveLeaf(
            Block leafBlock,
            String mode,
            Config config,
            Set<Location> removedLogs) {

        switch (mode) {
            case "aggressive":
                return true;

            case "radius":
                return !hasNearbyActiveLog(leafBlock.getLocation(), config, removedLogs, 4);

            case "smart":
            default:
                return isOrphanedLeaf(leafBlock, config, removedLogs);
        }
    }

    /**
     * Check if leaf is orphaned (not connected to any active logs)
     */
    private static boolean isOrphanedLeaf(Block leafBlock, Config config, Set<Location> removedLogs) {
        Location leafLoc = leafBlock.getLocation();

        if (!hasNearbyActiveLog(leafLoc, config, removedLogs, 6)) {
            return true;
        }

        Set<Location> visited = new HashSet<>();
        return !isConnectedToActiveLog(leafBlock, config, removedLogs, visited, 0);
    }

    private static boolean hasNearbyActiveLog(
            Location leafLoc,
            Config config,
            Set<Location> removedLogs,
            int checkRadius) {

        for (int x = -checkRadius; x <= checkRadius; x++) {
            for (int y = -checkRadius; y <= checkRadius; y++) {
                for (int z = -checkRadius; z <= checkRadius; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    Location checkLoc = leafLoc.clone().add(x, y, z);
                    Block block = checkLoc.getBlock();

                    if (isLog(block.getType(), config) && !isLocationInSet(checkLoc, removedLogs)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isConnectedToActiveLog(
            Block startBlock,
            Config config,
            Set<Location> removedLogs,
            Set<Location> visited,
            int depth) {

        if (depth > 8 || visited.size() > 100) {
            return false;
        }

        Location startLoc = startBlock.getLocation();
        if (visited.contains(startLoc)) {
            return false;
        }
        visited.add(startLoc);

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    Location checkLoc = startLoc.clone().add(x, y, z);
                    Block block = checkLoc.getBlock();
                    Material type = block.getType();

                    if (isLog(type, config) && !isLocationInSet(checkLoc, removedLogs)) {
                        return true;
                    }

                    if (isLeafBlock(type, config) && !visited.contains(checkLoc)) {
                        if (isConnectedToActiveLog(block, config, removedLogs, visited, depth + 1)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static void addNeighborsToQueue(
            Block current,
            Queue<Block> queue,
            Set<Location> visited,
            boolean connectedOnly) {

        for (int yOffset = -1; yOffset <= 1; yOffset++) {
            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    if (xOffset == 0 && yOffset == 0 && zOffset == 0) continue;

                    Block neighbor = current.getRelative(xOffset, yOffset, zOffset);
                    Location neighborLoc = neighbor.getLocation();

                    if (visited.contains(neighborLoc)) continue;

                    // Check connectivity if required
                    if (connectedOnly && blockNotConnected(current, neighbor)) {
                        continue;
                    }

                    visited.add(neighborLoc);
                    queue.add(neighbor);
                }
            }
        }
    }

    private static void addLeafNeighborsToQueue(
            Block current,
            Queue<Block> queue,
            Set<Location> visited,
            Location center,
            int radiusSquared) {

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    Block neighbor = current.getRelative(x, y, z);
                    Location neighborLoc = neighbor.getLocation();

                    if (visited.contains(neighborLoc)) continue;
                    if (neighborLoc.distanceSquared(center) > radiusSquared) continue;

                    visited.add(neighborLoc);
                    queue.add(neighbor);
                }
            }
        }
    }

    private static int getBitSetIndex(int x, int y, int z, int radius, int size) {
        int nx = x + radius;
        int ny = y + radius;
        int nz = z + radius;
        return nx + ny * size + nz * size * size;
    }

    private static boolean blockNotConnected(Block block1, Block block2) {
        int dx = Math.abs(block1.getX() - block2.getX());
        int dy = Math.abs(block1.getY() - block2.getY());
        int dz = Math.abs(block1.getZ() - block2.getZ());

        if (dx + dy + dz == 1) return false;

        // Not connected
        return true;
    }

    /**
     * Check if location is in a set (block coordinate comparison)
     */
    private static boolean isLocationInSet(Location loc, Set<Location> locationSet) {
        return locationSet.stream().anyMatch(setLoc ->
                setLoc.getBlockX() == loc.getBlockX() &&
                        setLoc.getBlockY() == loc.getBlockY() &&
                        setLoc.getBlockZ() == loc.getBlockZ() &&
                        Objects.equals(setLoc.getWorld(), loc.getWorld())
        );
    }

    public static boolean isLog(Material material, Config config) {
        return config.getLogTypes().contains(material);
    }

    public static boolean isLeafBlock(Material material, Config config) {
        return config.getLeafTypes().contains(material);
    }
}