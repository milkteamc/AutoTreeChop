package org.milkteamc.autotreechop.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.PlayerConfig;
import org.milkteamc.autotreechop.hooks.GriefPreventionHook;
import org.milkteamc.autotreechop.hooks.LandsHook;
import org.milkteamc.autotreechop.hooks.ResidenceHook;
import org.milkteamc.autotreechop.hooks.WorldGuardHook;

import java.util.*;

public class LeafRemovalUtils {

    private static final Map<String, Set<Location>> removedLogsPerSession = new HashMap<>();
    private static final Set<String> activeLeafRemovalSessions = new HashSet<>();

    public static void processLeafRemoval(Block originalLogBlock, Player player, AutoTreeChop plugin,
                                          Config config, PlayerConfig playerConfig,
                                          boolean worldGuardEnabled, boolean residenceEnabled,
                                          boolean griefPreventionEnabled, boolean landsEnabled,
                                          LandsHook landsHook, ResidenceHook residenceHook,
                                          GriefPreventionHook griefPreventionHook, WorldGuardHook worldGuardHook) {

        if (!config.isLeafRemovalEnabled()) {
            return;
        }

        if (!player.hasPermission("autotreechop.leaves")) {
            return;
        }

        String playerKey = player.getUniqueId().toString();
        if (activeLeafRemovalSessions.contains(playerKey)) {
            return;
        }

        String sessionId = playerKey + "_" + System.currentTimeMillis();

        // Create a copy of removed logs for this session
        Set<Location> sessionRemovedLogs = new HashSet<>();
        removedLogsPerSession.put(sessionId, sessionRemovedLogs);
        activeLeafRemovalSessions.add(playerKey);

        Runnable leafRemovalTask = () -> {
            Set<Location> checkedLeafLocations = new HashSet<>();
            Set<Location> processingLeafLocations = new HashSet<>();

            long delayTicks = Math.max(config.getLeafRemovalDelayTicks(), 60L);

            scheduleDelayedLeafRemoval(originalLogBlock, player, plugin, config, playerConfig,
                    worldGuardEnabled, residenceEnabled, griefPreventionEnabled, landsEnabled,
                    landsHook, residenceHook, griefPreventionHook, worldGuardHook,
                    checkedLeafLocations, processingLeafLocations, sessionId, playerKey, delayTicks);
        };

        if (AutoTreeChop.isFolia()) {
            plugin.getServer().getRegionScheduler().run(plugin, originalLogBlock.getLocation(),
                    task -> leafRemovalTask.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, leafRemovalTask);
        }
    }

    public static void trackRemovedLog(Location logLocation, String playerUUID) {
        String playerKey = playerUUID;
        for (Map.Entry<String, Set<Location>> entry : removedLogsPerSession.entrySet()) {
            if (entry.getKey().startsWith(playerKey + "_")) {
                entry.getValue().add(logLocation.clone());
            }
        }
    }

    private static void scheduleDelayedLeafRemoval(Block originalLogBlock, Player player, AutoTreeChop plugin,
                                                   Config config, PlayerConfig playerConfig,
                                                   boolean worldGuardEnabled, boolean residenceEnabled,
                                                   boolean griefPreventionEnabled, boolean landsEnabled,
                                                   LandsHook landsHook, ResidenceHook residenceHook,
                                                   GriefPreventionHook griefPreventionHook, WorldGuardHook worldGuardHook,
                                                   Set<Location> checkedLeafLocations, Set<Location> processingLeafLocations,
                                                   String sessionId, String playerKey, long delayTicks) {

        Runnable delayedTask = () -> startLeafRemoval(originalLogBlock, player, plugin, config, playerConfig,
                worldGuardEnabled, residenceEnabled, griefPreventionEnabled, landsEnabled,
                landsHook, residenceHook, griefPreventionHook, worldGuardHook,
                checkedLeafLocations, processingLeafLocations, sessionId, playerKey);

        if (AutoTreeChop.isFolia()) {
            plugin.getServer().getRegionScheduler().runDelayed(plugin, originalLogBlock.getLocation(),
                    (task) -> delayedTask.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, delayedTask, delayTicks);
        }
    }

    private static void startLeafRemoval(Block originalLogBlock, Player player, AutoTreeChop plugin,
                                         Config config, PlayerConfig playerConfig,
                                         boolean worldGuardEnabled, boolean residenceEnabled,
                                         boolean griefPreventionEnabled, boolean landsEnabled,
                                         LandsHook landsHook, ResidenceHook residenceHook,
                                         GriefPreventionHook griefPreventionHook, WorldGuardHook worldGuardHook,
                                         Set<Location> checkedLeafLocations, Set<Location> processingLeafLocations,
                                         String sessionId, String playerKey) {

        // Get removed logs from session
        Set<Location> removedLogs = removedLogsPerSession.getOrDefault(sessionId, new HashSet<>());

        Collection<Block> leavesToRemove = findLeavesToRemove(originalLogBlock, config.getLeafRemovalRadius(),
                checkedLeafLocations, config, removedLogs);

        if (leavesToRemove.isEmpty()) {
            cleanupSession(sessionId, playerKey);
            return;
        }

        List<Block> leavesList = new ArrayList<>(leavesToRemove);
        int batchSize = config.getLeafRemovalBatchSize();

        processLeavesBatch(leavesList, 0, batchSize, player, plugin, config, playerConfig,
                worldGuardEnabled, residenceEnabled, griefPreventionEnabled, landsEnabled,
                landsHook, residenceHook, griefPreventionHook, worldGuardHook, processingLeafLocations, sessionId, playerKey);
    }

    private static void processLeavesBatch(List<Block> leavesList, int startIndex, int batchSize,
                                           Player player, AutoTreeChop plugin, Config config, PlayerConfig playerConfig,
                                           boolean worldGuardEnabled, boolean residenceEnabled,
                                           boolean griefPreventionEnabled, boolean landsEnabled,
                                           LandsHook landsHook, ResidenceHook residenceHook,
                                           GriefPreventionHook griefPreventionHook, WorldGuardHook worldGuardHook,
                                           Set<Location> processingLeafLocations, String sessionId, String playerKey) {

        int endIndex = Math.min(startIndex + batchSize, leavesList.size());

        for (int i = startIndex; i < endIndex; i++) {
            Block leafBlock = leavesList.get(i);

            if (config.getLeafRemovalCountsTowardsLimit()) {
                if (!PermissionUtils.hasVipBlock(player, playerConfig, config) &&
                        playerConfig.getDailyBlocksBroken() >= config.getMaxBlocksPerDay()) {
                    break;
                }
            }

            removeLeafBlock(leafBlock, player, config, playerConfig, worldGuardEnabled, residenceEnabled,
                    griefPreventionEnabled, landsEnabled, landsHook, residenceHook,
                    griefPreventionHook, worldGuardHook, processingLeafLocations);
        }

        if (endIndex < leavesList.size()) {
            Runnable nextBatchTask = () -> processLeavesBatch(leavesList, endIndex, batchSize, player, plugin,
                    config, playerConfig, worldGuardEnabled, residenceEnabled, griefPreventionEnabled,
                    landsEnabled, landsHook, residenceHook, griefPreventionHook, worldGuardHook,
                    processingLeafLocations, sessionId, playerKey);

            if (AutoTreeChop.isFolia()) {
                plugin.getServer().getRegionScheduler().runDelayed(plugin, leavesList.get(endIndex).getLocation(),
                        (task) -> nextBatchTask.run(), 1L);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, nextBatchTask, 1L);
            }
        } else {
            cleanupSession(sessionId, playerKey);
        }
    }

    private static void cleanupSession(String sessionId, String playerKey) {
        removedLogsPerSession.remove(sessionId);
        activeLeafRemovalSessions.remove(playerKey);
    }

    private static boolean removeLeafBlock(Block leafBlock, Player player, Config config, PlayerConfig playerConfig,
                                           boolean worldGuardEnabled, boolean residenceEnabled,
                                           boolean griefPreventionEnabled, boolean landsEnabled,
                                           LandsHook landsHook, ResidenceHook residenceHook,
                                           GriefPreventionHook griefPreventionHook, WorldGuardHook worldGuardHook,
                                           Set<Location> processingLeafLocations) {

        Location leafLocation = leafBlock.getLocation();

        if (processingLeafLocations.contains(leafLocation)) {
            return false;
        }

        if (!TreeChopUtils.resCheck(player, leafLocation, residenceEnabled, residenceHook) ||
                !TreeChopUtils.landsCheck(player, leafLocation, landsEnabled, landsHook) ||
                !TreeChopUtils.gfCheck(player, leafLocation, griefPreventionEnabled, griefPreventionHook) ||
                !TreeChopUtils.wgCheck(player, leafLocation, worldGuardEnabled, worldGuardHook)) {
            return false;
        }

        processingLeafLocations.add(leafLocation);

        BlockBreakEvent breakEvent = new BlockBreakEvent(leafBlock, player);
        Bukkit.getPluginManager().callEvent(breakEvent);

        if (!breakEvent.isCancelled()) {
            if (config.getLeafRemovalVisualEffects()) {
                EffectUtils.showLeafRemovalEffect(player, leafBlock);
            }

            if (config.getLeafRemovalDropItems()) {
                leafBlock.breakNaturally();
            } else {
                leafBlock.setType(Material.AIR);
            }

            if (config.getLeafRemovalCountsTowardsLimit()) {
                playerConfig.incrementDailyBlocksBroken();
            }

            processingLeafLocations.remove(leafLocation);
            return true;
        }

        processingLeafLocations.remove(leafLocation);
        return false;
    }

    private static Collection<Block> findLeavesToRemove(Block centerBlock, int radius,
                                                        Set<Location> checkedLocations, Config config,
                                                        Set<Location> removedLogs) {
        Set<Block> leavesToRemove = new HashSet<>();
        Location center = centerBlock.getLocation();

        String mode = config.getLeafRemovalMode().toLowerCase();

        // Find all leaf blocks within radius
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location checkLoc = center.clone().add(x, y, z);

                    // Skip if too far (spherical check)
                    if (checkLoc.distanceSquared(center) > radius * radius) {
                        continue;
                    }

                    Block checkBlock = checkLoc.getBlock();

                    if (isLeafBlock(checkBlock.getType(), config) && !checkedLocations.contains(checkLoc)) {
                        boolean shouldRemove = false;

                        switch (mode) {
                            case "aggressive":
                                shouldRemove = true;
                                break;
                            case "radius":
                                shouldRemove = !hasNearbyActiveLog(checkLoc, config, removedLogs, 4);
                                break;
                            case "smart":
                            default:
                                shouldRemove = isOrphanedLeaf(checkBlock, config, removedLogs);
                                break;
                        }

                        if (shouldRemove) {
                            leavesToRemove.add(checkBlock);
                        }
                        checkedLocations.add(checkLoc);
                    }
                }
            }
        }

        return leavesToRemove;
    }

    private static boolean isOrphanedLeaf(Block leafBlock, Config config, Set<Location> removedLogs) {
        Location leafLoc = leafBlock.getLocation();

        // Check if there's any active log within 6 blocks
        if (!hasNearbyActiveLog(leafLoc, config, removedLogs, 6)) {
            return true; // No logs nearby, definitely orphaned
        }

        // Check if connected to remaining logs via leaves
        Set<Location> visited = new HashSet<>();
        if (!isConnectedToActiveLog(leafBlock, config, removedLogs, visited, 0)) {
            return true; // Not connected to any remaining logs
        }

        return false;
    }

    private static boolean hasNearbyActiveLog(Location leafLoc, Config config, Set<Location> removedLogs, int checkRadius) {
        for (int x = -checkRadius; x <= checkRadius; x++) {
            for (int y = -checkRadius; y <= checkRadius; y++) {
                for (int z = -checkRadius; z <= checkRadius; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    Location checkLoc = leafLoc.clone().add(x, y, z);
                    Block checkBlock = checkLoc.getBlock();

                    // Check if it's a log AND it wasn't removed
                    if (TreeChopUtils.isLog(checkBlock.getType(), config) &&
                            !isLocationInSet(checkLoc, removedLogs)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isLocationInSet(Location loc, Set<Location> locationSet) {
        for (Location setLoc : locationSet) {
            if (setLoc.getBlockX() == loc.getBlockX() &&
                    setLoc.getBlockY() == loc.getBlockY() &&
                    setLoc.getBlockZ() == loc.getBlockZ() &&
                    setLoc.getWorld().equals(loc.getWorld())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConnectedToActiveLog(Block startBlock, Config config, Set<Location> removedLogs,
                                                  Set<Location> visited, int depth) {
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
                    Block checkBlock = checkLoc.getBlock();
                    Material checkType = checkBlock.getType();

                    if (TreeChopUtils.isLog(checkType, config) && !isLocationInSet(checkLoc, removedLogs)) {
                        return true;
                    }

                    if (isLeafBlock(checkType, config) && !visited.contains(checkLoc)) {
                        if (isConnectedToActiveLog(checkBlock, config, removedLogs, visited, depth + 1)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public static boolean isLeafBlock(Material material, Config config) {
        return config.getLeafTypes().contains(material);
    }
}