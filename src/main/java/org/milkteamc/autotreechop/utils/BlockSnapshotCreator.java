package org.milkteamc.autotreechop.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.milkteamc.autotreechop.Config;

import java.util.*;

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
            Block startBlock,
            Config config,
            boolean connectedOnly,
            int maxBlocks) {

        Map<BlockSnapshot.LocationKey, Material> blockData = new HashMap<>();
        Queue<Block> queue = new LinkedList<>();
        Set<BlockSnapshot.LocationKey> visited = new HashSet<>();

        Material originalType = startBlock.getType();
        Location center = startBlock.getLocation();

        queue.add(startBlock);
        visited.add(new BlockSnapshot.LocationKey(center));

        // Quick BFS to find all connected logs
        while (!queue.isEmpty() && blockData.size() < maxBlocks) {
            Block current = queue.poll();
            BlockSnapshot.LocationKey key = new BlockSnapshot.LocationKey(current.getLocation());
            Material type = current.getType();

            // Store block data
            blockData.put(key, type);

            // Only continue if it's a log
            if (!isLog(type, config)) {
                continue;
            }

            // Check same type if required
            if (config.isStopChoppingIfDifferentTypes() && type != originalType) {
                continue;
            }

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

        return new BlockSnapshot(blockData, startBlock.getWorld(), center);
    }

    /**
     * Capture a spherical region around center for leaf processing
     *
     * @param centerBlock Center of the sphere
     * @param radius Radius in blocks
     * @param config Plugin configuration
     * @return BlockSnapshot containing the spherical region
     */
    public static BlockSnapshot captureLeafRegion(
            Block centerBlock,
            int radius,
            Config config) {

        Map<BlockSnapshot.LocationKey, Material> blockData = new HashMap<>();
        Location center = centerBlock.getLocation();
        int radiusSquared = radius * radius;

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        // Scan spherical region
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    // Spherical check
                    if (x * x + y * y + z * z > radiusSquared) {
                        continue;
                    }

                    Block block = center.getWorld().getBlockAt(cx + x, cy + y, cz + z);
                    Material type = block.getType();

                    // Only store leaves and logs
                    if (isLeafBlock(type, config) || isLog(type, config)) {
                        BlockSnapshot.LocationKey key = new BlockSnapshot.LocationKey(cx + x, cy + y, cz + z);
                        blockData.put(key, type);
                    }
                }
            }
        }

        return new BlockSnapshot(blockData, centerBlock.getWorld(), center);
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