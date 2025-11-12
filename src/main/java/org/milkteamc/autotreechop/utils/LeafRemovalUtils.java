package org.milkteamc.autotreechop.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.PlayerConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LeafRemovalUtils {

    private final AutoTreeChop plugin;
    private final AsyncTaskScheduler scheduler;
    private final BatchProcessor batchProcessor;
    private final SessionManager sessionManager;

    private final Set<Location> processingLeafLocations = ConcurrentHashMap.newKeySet();

    public LeafRemovalUtils(AutoTreeChop plugin) {
        this.plugin = plugin;
        this.scheduler = new AsyncTaskScheduler(plugin);
        this.batchProcessor = new BatchProcessor(plugin, scheduler);
        this.sessionManager = SessionManager.getInstance();
    }

    public void processLeafRemoval(Block originalLogBlock, Player player, Config config,
                                   PlayerConfig playerConfig,
                                   ProtectionCheckUtils.ProtectionHooks hooks) {

        if (!config.isLeafRemovalEnabled()) {
            return;
        }

        if (!player.hasPermission("autotreechop.leaves")) {
            return;
        }

        String playerKey = player.getUniqueId().toString();

        // Check if player already has an active leaf removal session
        if (sessionManager.hasActiveLeafRemovalSession(playerKey)) {
            return;
        }

        // Start a new session
        String sessionId = sessionManager.startLeafRemovalSession(playerKey);
        if (sessionId == null) {
            return; // Failed to start session
        }

        // Schedule delayed leaf removal
        long delayTicks = config.getLeafRemovalDelayTicks();

        scheduler.scheduleDelayed(
                originalLogBlock.getLocation(),
                () -> startLeafRemoval(originalLogBlock, player, config, playerConfig, hooks, sessionId, playerKey),
                delayTicks
        );
    }

    private void startLeafRemoval(Block originalLogBlock, Player player, Config config,
                                  PlayerConfig playerConfig,
                                  ProtectionCheckUtils.ProtectionHooks hooks,
                                  String sessionId, String playerKey) {

        // Discovery task (can be async)
        Runnable discoveryTask = () -> {
            Set<Location> removedLogs = sessionManager.getRemovedLogs(sessionId);

            Set<Block> leavesToRemove;
            int radius = config.getLeafRemovalRadius();

            // Choose discovery method based on radius
            if (radius > 10) {
                // For large radius, use BFS to avoid scanning too many blocks
                leavesToRemove = BlockDiscoveryUtils.discoverLeavesBFS(
                        originalLogBlock, radius, config, removedLogs
                );
            } else {
                // For small radius, radial scan with BitSet is faster
                leavesToRemove = BlockDiscoveryUtils.discoverLeavesRadial(
                        originalLogBlock, radius, config, removedLogs
                );
            }

            // Execute removal on main thread
            Runnable executionTask = () ->
                    executeLeafRemoval(leavesToRemove, player, config, playerConfig,
                            hooks, sessionId, playerKey);

            scheduler.runTask(executionTask);
        };

        scheduler.runTaskAsync(discoveryTask);
    }

    private void executeLeafRemoval(Set<Block> leavesToRemove, Player player,
                                    Config config, PlayerConfig playerConfig,
                                    ProtectionCheckUtils.ProtectionHooks hooks,
                                    String sessionId, String playerKey) {

        if (leavesToRemove.isEmpty()) {
            sessionManager.endLeafRemovalSession(sessionId, playerKey);
            return;
        }

        List<Location> leavesList = new ArrayList<>();
        for (Block block : leavesToRemove) {
            leavesList.add(block.getLocation());
        }

        int batchSize = config.getLeafRemovalBatchSize();

        batchProcessor.processBatchWithTermination(
                leavesList,
                0,
                batchSize,
                (location, index) -> {
                    if (config.getLeafRemovalCountsTowardsLimit()) {
                        if (!PermissionUtils.hasVipBlock(player, playerConfig, config) &&
                                playerConfig.getDailyBlocksBroken() >= config.getMaxBlocksPerDay()) {
                            return false; // Stop processing
                        }
                    }

                    Block leafBlock = location.getBlock();
                    boolean removed = removeLeafBlock(leafBlock, player, config, playerConfig, hooks);

                    return true; // Continue processing
                },
                // On complete
                () -> sessionManager.endLeafRemovalSession(sessionId, playerKey)
        );
    }

    private boolean removeLeafBlock(Block leafBlock, Player player, Config config,
                                    PlayerConfig playerConfig,
                                    ProtectionCheckUtils.ProtectionHooks hooks) {

        Location leafLocation = leafBlock.getLocation();

        if (processingLeafLocations.contains(leafLocation)) {
            return false;
        }

        if (!ProtectionCheckUtils.canModifyBlock(player, leafLocation, hooks)) {
            return false;
        }

        processingLeafLocations.add(leafLocation);

        if (config.isCallBlockBreakEvent()) {
            BlockBreakEvent breakEvent = new BlockBreakEvent(leafBlock, player);
            plugin.getServer().getPluginManager().callEvent(breakEvent);
            if (breakEvent.isCancelled()) {
                processingLeafLocations.remove(leafLocation);
                return false;
            }
        }

        if (config.getLeafRemovalVisualEffects()) {
            EffectUtils.showLeafRemovalEffect(player, leafBlock);
        }

        if (config.getLeafRemovalDropItems()) {
            leafBlock.breakNaturally();
        } else {
            leafBlock.setType(Material.AIR, false);
        }

        if (config.getLeafRemovalCountsTowardsLimit()) {
            playerConfig.incrementDailyBlocksBroken();
        }

        processingLeafLocations.remove(leafLocation);
        return true;
    }
}