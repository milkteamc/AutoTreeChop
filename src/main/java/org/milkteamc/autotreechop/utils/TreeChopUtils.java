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

import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XSound;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.MessageKeys;
import org.milkteamc.autotreechop.PlayerConfig;

public class TreeChopUtils {

    private static final Random random = new Random();

    private final AutoTreeChop plugin;
    private final AsyncTaskScheduler scheduler;
    private final BatchProcessor batchProcessor;
    private final SessionManager sessionManager;
    private final Set<Location> processingLeafLocations = ConcurrentHashMap.newKeySet();

    public TreeChopUtils(AutoTreeChop plugin) {
        this.plugin = plugin;
        this.scheduler = new AsyncTaskScheduler(plugin);
        this.batchProcessor = new BatchProcessor(plugin, scheduler);
        this.sessionManager = SessionManager.getInstance();
    }

    private static boolean hasEnoughDurability(Player player, int blockCount, Config config) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType().getMaxDurability() <= 0) {
            return true;
        }

        if (!(tool.getItemMeta() instanceof Damageable damageableMeta)) {
            return true;
        }

        int currentDamage = damageableMeta.getDamage();
        int maxDurability = tool.getType().getMaxDurability();
        int remainingDurability = maxDurability - currentDamage;

        int unbreakingLevel = getUnbreakingLevel(tool);

        int damagePerHit = config.isToolDamage() ? config.getToolDamageDecrease() : 1;
        int estimatedDamage;
        if (config.getRespectUnbreaking() && unbreakingLevel > 0) {
            estimatedDamage = (blockCount * damagePerHit) / (unbreakingLevel + 1);
        } else {
            estimatedDamage = blockCount * damagePerHit;
        }

        return remainingDurability > estimatedDamage;
    }

    private static void applyToolDamage(Player player, int blocksBroken, Config config) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType().getMaxDurability() <= 0) {
            return;
        }

        if (!(tool.getItemMeta() instanceof Damageable damageableMeta)) {
            return;
        }

        int unbreakingLevel = getUnbreakingLevel(tool);
        int damageToApply = 0;

        for (int i = 0; i < blocksBroken * config.getToolDamageDecrease(); i++) {
            if (shouldApplyDurabilityLoss(unbreakingLevel, config)) {
                damageToApply++;
            }
        }

        if (damageToApply == 0) return;

        int currentDamage = damageableMeta.getDamage();
        int newDamage = currentDamage + damageToApply;

        if (newDamage >= tool.getType().getMaxDurability()) {
            tool.setAmount(0);
            try {
                XSound.ENTITY_ITEM_BREAK.play(player.getLocation(), 1.0f, 1.0f);
            } catch (Exception ignored) {
            }
        } else {
            damageableMeta.setDamage(newDamage);
            tool.setItemMeta(damageableMeta);
        }
    }

    private static int getUnbreakingLevel(ItemStack item) {
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            return item.getEnchantmentLevel(XEnchantment.UNBREAKING.get());
        }
        return 0;
    }

    private static boolean shouldApplyDurabilityLoss(int unbreakingLevel, Config config) {
        if (unbreakingLevel <= 0 || !config.getRespectUnbreaking()) {
            return true;
        }
        return random.nextInt(unbreakingLevel + 1) == 0;
    }

    public static boolean isTool(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null) {
            return false;
        }

        XMaterial xMat = XMaterial.matchXMaterial(item);
        if (xMat == XMaterial.AIR) {
            return false;
        }

        String materialName = xMat.name();

        if (materialName.endsWith("_AXE")
                || materialName.endsWith("_HOE")
                || materialName.endsWith("_PICKAXE")
                || materialName.endsWith("_SHOVEL")
                || materialName.endsWith("_SWORD")) {
            return true;
        }

        return xMat == XMaterial.SHEARS || xMat == XMaterial.FISHING_ROD || xMat == XMaterial.FLINT_AND_STEEL;
    }

    public void chopTree(
            Block block,
            Player player,
            boolean connectedBlocks,
            ItemStack tool,
            Location location,
            Config config,
            PlayerConfig playerConfig,
            ProtectionCheckUtils.ProtectionHooks hooks) {

        if (!player.hasPermission("autotreechop.use")) {
            playerConfig.setAutoTreeChopEnabled(false);
            return;
        }

        // Initial protection check
        if (!ProtectionCheckUtils.canModifyBlock(player, location, hooks)) {
            return;
        }

        UUID playerUUID = player.getUniqueId();
        if (sessionManager.isLocationProcessing(playerUUID, block.getLocation())) {
            return;
        }

        if (!BlockDiscoveryUtils.isLog(block.getType(), config)) {
            return;
        }

        if (config.getMustUseTool() && !isTool(player)) {
            return;
        }

        // Mark location as processing
        sessionManager.addTreeChopLocations(playerUUID, Collections.singleton(block.getLocation()));

        try {
            BlockSnapshot treeSnapshot = BlockSnapshotCreator.captureTreeRegion(
                    block, config, connectedBlocks, config.getMaxDiscoveryBlocks());

            Location startLocation = block.getLocation().clone();

            Runnable asyncDiscovery = () -> {
                try {
                    Set<Location> treeBlocks = BlockDiscoveryUtils.discoverTreeBFS(
                            treeSnapshot, startLocation, config, connectedBlocks, config.getMaxTreeSize());

                    // PHASE 3: Back to sync for validation and execution
                    Runnable validationTask =
                            () -> validateAndExecuteChop(treeBlocks, block, player, tool, config, playerConfig, hooks);

                    scheduler.runTaskAtLocation(startLocation, validationTask);

                } catch (Exception e) {
                    plugin.getLogger().warning("Error during async tree discovery: " + e.getMessage());
                    e.printStackTrace();
                    sessionManager.clearTreeChopSession(playerUUID);
                }
            };

            scheduler.runTaskAsync(asyncDiscovery);

        } catch (Exception e) {
            plugin.getLogger().warning("Error capturing tree snapshot: " + e.getMessage());
            e.printStackTrace();
            sessionManager.clearTreeChopSession(playerUUID);
        }
    }

    private void validateAndExecuteChop(
            Set<Location> treeBlocks,
            Block originalBlock,
            Player player,
            ItemStack tool,
            Config config,
            PlayerConfig playerConfig,
            ProtectionCheckUtils.ProtectionHooks hooks) {

        UUID playerUUID = player.getUniqueId();

        if (!player.hasPermission("autotreechop.use")) {
            sessionManager.clearTreeChopSession(playerUUID);
            return;
        }

        if (treeBlocks.isEmpty()) {
            sessionManager.clearTreeChopSession(playerUUID);
            return;
        }

        if (treeBlocks.size() > config.getMaxTreeSize()) {
            AutoTreeChop.sendMessage(player, MessageKeys.HIT_MAX_BLOCK);
            sessionManager.clearTreeChopSession(playerUUID);
            return;
        }

        if (config.getLimitUsage()) {
            if (!PermissionUtils.hasVipBlock(player, playerConfig, config)) {
                if (playerConfig.getDailyBlocksBroken() + treeBlocks.size() > config.getMaxBlocksPerDay()) {
                    AutoTreeChop.sendMessage(player, MessageKeys.HIT_MAX_BLOCK);
                    sessionManager.clearTreeChopSession(playerUUID);
                    return;
                }
            }
        }

        if (config.isToolDamage() && !hasEnoughDurability(player, treeBlocks.size(), config)) {
            sessionManager.clearTreeChopSession(playerUUID);
            return;
        }

        playerConfig.incrementDailyUses();

        sessionManager.addTreeChopLocations(playerUUID, treeBlocks);

        executeTreeChop(treeBlocks, player, tool, config, playerConfig, hooks, originalBlock);
    }

    private void executeTreeChop(
            Set<Location> treeBlocks,
            Player player,
            ItemStack tool,
            Config config,
            PlayerConfig playerConfig,
            ProtectionCheckUtils.ProtectionHooks hooks,
            Block originalBlock) {

        List<Location> blockList = new ArrayList<>(treeBlocks);
        int batchSize = config.getChopBatchSize();
        int totalBlocks = blockList.size();
        UUID playerUUID = player.getUniqueId();

        Location centerLocation = originalBlock.getLocation().clone();
        Map<Material, Location> logTypesForReplant = new HashMap<>();
        Set<Location> actuallyRemovedLogs = ConcurrentHashMap.newKeySet();

        BlockSnapshot leafSnapshot = null;
        if (config.isLeafRemovalEnabled()) {
            try {
                leafSnapshot =
                        BlockSnapshotCreator.captureLeafRegion(originalBlock, config.getLeafRemovalRadius(), config);
            } catch (Exception e) {
                plugin.getLogger().warning("Error pre-capturing leaf snapshot: " + e.getMessage());
            }
        }

        BlockSnapshot finalLeafSnapshot = leafSnapshot;

        batchProcessor.processBatch(
                blockList,
                0,
                batchSize,
                (location, index) -> {
                    Block block = location.getBlock();

                    // Re-check block type (may have changed between phases)
                    if (!BlockDiscoveryUtils.isLog(block.getType(), config)) {
                        return;
                    }

                    // Re-check protection at execution time
                    if (!ProtectionCheckUtils.canModifyBlock(player, location, hooks)) {
                        return;
                    }

                    Material originalLogType = block.getType();

                    // Track the lowest-Y log of each type for replanting
                    Location existingLoc = logTypesForReplant.get(originalLogType);
                    if (existingLoc == null || location.getBlockY() < existingLoc.getBlockY()) {
                        logTypesForReplant.put(originalLogType, location.clone());
                    }

                    if (config.isCallBlockBreakEvent()) {
                        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
                        plugin.getServer().getPluginManager().callEvent(breakEvent);
                        if (breakEvent.isCancelled()) {
                            return;
                        }
                    }

                    if (config.getPlayBreakSound()) {
                        XSound.BLOCK_WOOD_BREAK.play(location, 1.0f, 1.0f);
                    }
                    block.breakNaturally();

                    actuallyRemovedLogs.add(location);
                    sessionManager.trackRemovedLogForPlayer(playerUUID.toString(), location);
                    playerConfig.incrementDailyBlocksBroken();
                },
                () -> {
                    // After all logs are removed
                    if (config.isToolDamage()) {
                        applyToolDamage(player, totalBlocks, config);
                    }

                    // Handle leaf removal
                    if (config.isLeafRemovalEnabled() && finalLeafSnapshot != null) {
                        long delay = config.getLeafRemovalDelayTicks();

                        Runnable leafTask = () -> processLeafRemovalWithPreCapturedSnapshot(
                                finalLeafSnapshot,
                                centerLocation,
                                player,
                                config,
                                playerConfig,
                                hooks,
                                actuallyRemovedLogs);

                        scheduler.scheduleDelayed(centerLocation, leafTask, delay);
                    }

                    // Handle replanting
                    if (TreeReplantUtils.isReplantEnabledForPlayer(player, config)) {
                        for (Map.Entry<Material, Location> entry : logTypesForReplant.entrySet()) {
                            Block blockToReplant = entry.getValue().getBlock();
                            TreeReplantUtils.scheduleReplant(
                                    player,
                                    blockToReplant,
                                    entry.getKey(),
                                    plugin,
                                    config,
                                    hooks.worldGuardEnabled,
                                    hooks.residenceEnabled,
                                    hooks.griefPreventionEnabled,
                                    hooks.landsEnabled,
                                    hooks.lands,
                                    hooks.residence,
                                    hooks.griefPrevention,
                                    hooks.worldGuard,
                                    actuallyRemovedLogs);
                        }
                    }

                    plugin.getCooldownManager().setCooldown(player, playerUUID, config);

                    sessionManager.removeTreeChopLocations(playerUUID, blockList);
                });
    }

    private void processLeafRemovalWithPreCapturedSnapshot(
            BlockSnapshot leafSnapshot,
            Location centerLocation,
            Player player,
            Config config,
            PlayerConfig playerConfig,
            ProtectionCheckUtils.ProtectionHooks hooks,
            Set<Location> removedLogs) {

        if (!config.isLeafRemovalEnabled()) {
            return;
        }

        if (!player.hasPermission("autotreechop.leaves")) {
            return;
        }

        String playerKey = player.getUniqueId().toString();

        if (sessionManager.hasActiveLeafRemovalSession(playerKey)) {
            return;
        }

        String sessionId = sessionManager.startLeafRemovalSession(playerKey);
        if (sessionId == null) {
            return;
        }

        Runnable asyncLeafCalculation = () -> {
            try {
                Set<Location> leavesToRemove;
                int radius = config.getLeafRemovalRadius();

                if ("smart".equalsIgnoreCase(config.getLeafRemovalMode())) {
                    leavesToRemove = BlockDiscoveryUtils.discoverLeavesBFS(
                            leafSnapshot, centerLocation, radius, config, removedLogs);
                } else {
                    leavesToRemove = BlockDiscoveryUtils.discoverLeavesRadial(
                            leafSnapshot, centerLocation, radius, config, removedLogs);
                }

                Runnable removalTask = () ->
                        executeLeafRemoval(leavesToRemove, player, config, playerConfig, hooks, sessionId, playerKey);

                scheduler.runTaskAtLocation(centerLocation, removalTask);

            } catch (Exception e) {
                plugin.getLogger().warning("Error during async leaf calculation: " + e.getMessage());
                e.printStackTrace();
                sessionManager.endLeafRemovalSession(sessionId, playerKey);
            }
        };

        if (config.isLeafRemovalAsync()) {
            scheduler.runTaskAsync(asyncLeafCalculation);
        } else {
            asyncLeafCalculation.run();
        }
    }

    private void executeLeafRemoval(
            Set<Location> leavesToRemove,
            Player player,
            Config config,
            PlayerConfig playerConfig,
            ProtectionCheckUtils.ProtectionHooks hooks,
            String sessionId,
            String playerKey) {

        if (leavesToRemove.isEmpty()) {
            sessionManager.endLeafRemovalSession(sessionId, playerKey);
            return;
        }

        List<Location> leafList = new ArrayList<>(leavesToRemove);
        int batchSize = config.getLeafRemovalBatchSize();

        batchProcessor.processBatchWithTermination(
                leafList,
                0,
                batchSize,
                (location, index) -> {
                    if (config.getLeafRemovalCountsTowardsLimit()) {
                        if (!PermissionUtils.hasVipBlock(player, playerConfig, config)
                                && playerConfig.getDailyBlocksBroken() >= config.getMaxBlocksPerDay()) {
                            return false;
                        }
                    }

                    Block leafBlock = location.getBlock();
                    removeLeafBlock(leafBlock, player, config, playerConfig, hooks);
                    return true;
                },
                () -> sessionManager.endLeafRemovalSession(sessionId, playerKey));
    }

    private boolean removeLeafBlock(
            Block leafBlock,
            Player player,
            Config config,
            PlayerConfig playerConfig,
            ProtectionCheckUtils.ProtectionHooks hooks) {

        Location leafLocation = leafBlock.getLocation();

        if (!processingLeafLocations.add(leafLocation)) {
            return false;
        }

        try {
            if (!BlockDiscoveryUtils.isLeafBlock(leafBlock.getType(), config)) {
                return false;
            }

            if (!ProtectionCheckUtils.canModifyBlock(player, leafLocation, hooks)) {
                return false;
            }

            if (config.isCallBlockBreakEvent()) {
                BlockBreakEvent breakEvent = new BlockBreakEvent(leafBlock, player);
                plugin.getServer().getPluginManager().callEvent(breakEvent);
                if (breakEvent.isCancelled()) {
                    return false;
                }
            }

            if (config.getLeafRemovalVisualEffects()) {
                EffectUtils.showLeafRemovalEffect(player, leafBlock);
            }

            if (config.getLeafRemovalDropItems()) {
                leafBlock.breakNaturally();
            } else {
                leafBlock.setType(XMaterial.AIR.get(), false);
            }

            if (config.getLeafRemovalCountsTowardsLimit()) {
                playerConfig.incrementDailyBlocksBroken();
            }

            return true;

        } finally {
            processingLeafLocations.remove(leafLocation);
        }
    }
}
