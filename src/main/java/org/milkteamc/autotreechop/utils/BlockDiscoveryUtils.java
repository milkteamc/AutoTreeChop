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

            if (!ProtectionCheckUtils.canModifyBlock(player, loc, hooks)) {
                continue;
            }

            if (!isLog(current.getType(), config)) {
                continue;
            }

            if (config.isStopChoppingIfDifferentTypes() && current.getType() != originalType) {
                continue;
            }

            treeBlocks.add(loc);
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
        Set<Location> allNearbyLogs = buildNearbyLogsSet(center, radius + 5, config);

        while (!queue.isEmpty()) {
            Block current = queue.poll();
            Location loc = current.getLocation();

            if (loc.distanceSquared(center) > radiusSquared) {
                continue;
            }

            Material type = current.getType();

            if (isLeafBlock(type, config)) {
                boolean shouldRemove = shouldRemoveLeaf(
                        current, mode, config, removedLogs, radius, allNearbyLogs
                );
                if (shouldRemove) {
                    leaves.add(current);
                }
                addLeafNeighborsToQueue(current, queue, visited, center, radiusSquared);
            }
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

        int size = (radius * 2 + 1);
        BitSet visited = new BitSet(size * size * size);
        int radiusSquared = radius * radius;

        Set<Location> allNearbyLogs = buildNearbyLogsSet(center, radius + 5, config);

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location loc = center.clone().add(x, y, z);

                    if (loc.distanceSquared(center) > radiusSquared) {
                        continue;
                    }

                    int index = getBitSetIndex(x, y, z, radius, size);
                    if (visited.get(index)) {
                        continue;
                    }
                    visited.set(index);

                    Block block = loc.getBlock();
                    Material type = block.getType();

                    if (isLeafBlock(type, config)) {
                        boolean shouldRemove = shouldRemoveLeaf(
                                block, mode, config, removedLogs, radius, allNearbyLogs
                        );
                        if (shouldRemove) {
                            leaves.add(block);
                        }
                    }
                }
            }
        }

        return leaves;
    }

    private static Set<Location> buildNearbyLogsSet(Location center, int scanRadius, Config config) {
        Set<Location> logs = new HashSet<>();
        int radiusSquared = scanRadius * scanRadius;

        for (int x = -scanRadius; x <= scanRadius; x++) {
            for (int y = -scanRadius; y <= scanRadius; y++) {
                for (int z = -scanRadius; z <= scanRadius; z++) {
                    if (x * x + y * y + z * z > radiusSquared) continue;

                    Location loc = center.clone().add(x, y, z);
                    Block block = loc.getBlock();

                    if (isLog(block.getType(), config)) {
                        logs.add(normalizeLocation(loc));
                    }
                }
            }
        }
        return logs;
    }

    private static boolean shouldRemoveLeaf(
            Block leafBlock,
            String mode,
            Config config,
            Set<Location> removedLogs,
            int searchRadius,
            Set<Location> allNearbyLogs) {

        switch (mode) {
            case "aggressive":
                return true;

            case "radius":
                // 檢查附近是否有「未被移除」的原木
                return !hasActiveLogNearby(leafBlock.getLocation(), removedLogs,
                        allNearbyLogs, searchRadius);

            case "smart":
            default:
                // Smart 模式:結合距離和連接性
                return isOrphanedLeaf(leafBlock, config, removedLogs,
                        searchRadius, allNearbyLogs);
        }
    }

    /**
     * 優化的孤立葉子判斷
     * 策略:
     * 1. 優先檢查近距離(4格)是否有活躍原木
     * 2. 如果有,進行連接性檢查(但範圍有限)
     * 3. 使用預建的原木集合加速查找
     */
    private static boolean isOrphanedLeaf(
            Block leafBlock,
            Config config,
            Set<Location> removedLogs,
            int searchRadius,
            Set<Location> allNearbyLogs) {

        Location leafLoc = leafBlock.getLocation();

        // 第一步:檢查附近 4 格內是否有活躍原木(快速判斷)
        if (!hasActiveLogNearby(leafLoc, removedLogs, allNearbyLogs, 4)) {
            return true; // 附近完全沒原木,直接移除
        }

        // 第二步:檢查中距離(搜尋半徑內)是否有活躍原木
        if (!hasActiveLogNearby(leafLoc, removedLogs, allNearbyLogs, searchRadius)) {
            return true; // 搜尋範圍內沒原木,移除
        }

        // 第三步:進行有限的連接性檢查
        // 關鍵:只檢查「距離葉子較近」的連接
        Set<Location> visited = new HashSet<>();
        return !isConnectedToActiveLog(
                leafBlock, config, removedLogs, allNearbyLogs, visited, 0, 8, 80
        );
    }

    /**
     * 快速檢查附近是否有活躍原木
     * 使用預建的原木集合,避免重複掃描世界
     */
    private static boolean hasActiveLogNearby(
            Location leafLoc,
            Set<Location> removedLogs,
            Set<Location> allNearbyLogs,
            int checkRadius) {

        int radiusSquared = checkRadius * checkRadius;

        for (int x = -checkRadius; x <= checkRadius; x++) {
            for (int y = -checkRadius; y <= checkRadius; y++) {
                for (int z = -checkRadius; z <= checkRadius; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    if (x * x + y * y + z * z > radiusSquared) continue;

                    Location checkLoc = leafLoc.clone().add(x, y, z);
                    Location normalized = normalizeLocation(checkLoc);

                    // 使用預建集合快速查找
                    if (allNearbyLogs.contains(normalized) &&
                            !isLocationInSet(checkLoc, removedLogs)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 快速的連接性檢查
     * 使用預建的原木集合,減少世界查詢
     */
    private static boolean isConnectedToActiveLog(
            Block startBlock,
            Config config,
            Set<Location> removedLogs,
            Set<Location> allNearbyLogs,
            Set<Location> visited,
            int depth,
            int maxDepth,
            int maxVisited) {

        if (depth > maxDepth || visited.size() > maxVisited) {
            return false;
        }

        Location startLoc = normalizeLocation(startBlock.getLocation());
        if (visited.contains(startLoc)) {
            return false;
        }
        visited.add(startLoc);

        // 檢查 26 個鄰居
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    Location checkLoc = startLoc.clone().add(x, y, z);
                    Location normalized = normalizeLocation(checkLoc);

                    // 檢查是否為活躍原木
                    if (allNearbyLogs.contains(normalized) &&
                            !isLocationInSet(checkLoc, removedLogs)) {
                        return true; // 找到活躍原木
                    }

                    // 繼續透過葉子搜尋
                    Block block = checkLoc.getBlock();
                    if (isLeafBlock(block.getType(), config) && !visited.contains(normalized)) {
                        if (isConnectedToActiveLog(
                                block, config, removedLogs, allNearbyLogs,
                                visited, depth + 1, maxDepth, maxVisited
                        )) {
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

        return true;
    }

    /**
     * 標準化位置(只保留方塊座標)
     */
    private static Location normalizeLocation(Location loc) {
        return new Location(
                loc.getWorld(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        );
    }

    /**
     * 檢查位置是否在集合中
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