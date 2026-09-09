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
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
import org.milkteamc.autotreechop.utils.ConfirmationManager.ConfirmReason;

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

    private boolean isCurrentPlayer(Player player, PlayerConfig playerConfig) {
        return RegionAccess.owns(player)
                && player.isOnline()
                && plugin.getDataManager().getPlayerConfig(player.getUniqueId()) == playerConfig;
    }

    private static boolean hasEnoughDurability(Player player, int blockCount, Config config) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType().getMaxDurability() <= 0) {
            return true;
        }

        if (!(tool.getItemMeta() instanceof Damageable damageableMeta)) {
            return true;
        }

        if (damageableMeta.isUnbreakable()) return true;
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

    private static void applyToolDamage(Player player, ItemStack tool, int blocksBroken, Config config) {
        if (tool == null || tool.getType().getMaxDurability() <= 0) {
            return;
        }

        if (!(tool.getItemMeta() instanceof Damageable damageableMeta)) {
            return;
        }

        if (damageableMeta.isUnbreakable()) return;
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

        chopTree(block, player, connectedBlocks, tool, location, config, playerConfig, hooks, true, null);
    }

    public void chopTree(
            Block block,
            Player player,
            boolean connectedBlocks,
            ItemStack tool,
            Location location,
            Config config,
            PlayerConfig playerConfig,
            ProtectionCheckUtils.ProtectionHooks hooks,
            boolean hasLeaves,
            ConfirmReason confirmedReason) {

        if (!isCurrentPlayer(player, playerConfig)) return;
        if (!RegionAccess.owns(block.getLocation())) return;

        if (!player.hasPermission("autotreechop.use")) {
            playerConfig.setAutoTreeChopEnabled(false);
            return;
        }

        // Initial protection check
        if (!ProtectionCheckUtils.canModifyBlock(player, location, hooks)) {
            return;
        }

        UUID playerUUID = player.getUniqueId();
        if (sessionManager.hasActiveTreeChopSession(playerUUID)) {
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
            if (!treeSnapshot.isComplete()) {
                AutoTreeChop.sendMessage(player, MessageKeys.HIT_MAX_BLOCK);
                sessionManager.clearTreeChopSession(playerUUID);
                return;
            }

            Location startLocation = block.getLocation().clone();

            Runnable asyncDiscovery = () -> {
                try {
                    Set<Location> treeBlocks = BlockDiscoveryUtils.discoverTreeBFS(
                            treeSnapshot, startLocation, config, connectedBlocks, config.getMaxTreeSize());

                    // PHASE 3: Back to sync for validation and execution
                    Runnable validationTask = () -> validateAndExecuteChop(
                            treeBlocks, block, player, tool, config, playerConfig, hooks, hasLeaves, confirmedReason);

                    scheduler.runTaskAtLocation(startLocation, validationTask);

                } catch (Exception e) {
                    plugin.getLogger().warning("Error during async tree discovery: " + e.getMessage());
                    e.printStackTrace();
                    if (plugin.getDataManager().getPlayerConfig(playerUUID) == playerConfig)
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
            ProtectionCheckUtils.ProtectionHooks hooks,
            boolean hasLeaves,
            ConfirmReason confirmedReason) {

        UUID playerUUID = player.getUniqueId();

        if (!isCurrentPlayer(player, playerConfig)) {
            if (plugin.getDataManager().getPlayerConfig(playerUUID) == playerConfig)
                sessionManager.clearTreeChopSession(playerUUID);
            return;
        }

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

        if (plugin.getCooldownManager().isInCooldown(playerUUID)) {
            sessionManager.clearTreeChopSession(playerUUID);
            return;
        }
        if (!PermissionUtils.canUse(player, playerConfig, config)) {
            AutoTreeChop.sendMessage(player, MessageKeys.HIT_MAX_USAGE);
            sessionManager.clearTreeChopSession(playerUUID);
            return;
        }
        if (!PermissionUtils.canBreakBlocks(player, playerConfig, config, treeBlocks.size())) {
            AutoTreeChop.sendMessage(player, MessageKeys.HIT_MAX_BLOCK);
            sessionManager.clearTreeChopSession(playerUUID);
            return;
        }

        if (!Objects.equals(tool, player.getInventory().getItemInMainHand())) {
            sessionManager.clearTreeChopSession(playerUUID);
            return;
        }

        if (config.isToolDamage() && !hasEnoughDurability(player, treeBlocks.size(), config)) {
            sessionManager.clearTreeChopSession(playerUUID);
            return;
        }

        TreeGrounding.Result grounding = TreeGrounding.inspect(treeBlocks, config);
        if (grounding == null) {
            AutoTreeChop.sendMessage(player, MessageKeys.TREE_SCAN_INCOMPLETE);
            sessionManager.clearTreeChopSession(playerUUID);
            return;
        }

        ConfirmationManager confirmations = plugin.getConfirmationManager();
        if (confirmedReason == null) {
            var pending =
                    confirmations.consumePendingConfirmationForBlock(playerUUID, originalBlock.getLocation(), tool);
            if (pending != null) confirmedReason = pending.reason();
        }
        ConfirmReason reason = !grounding.grounded() && confirmedReason != ConfirmReason.FLOATING
                ? ConfirmReason.FLOATING
                : confirmedReason == null ? confirmations.getConfirmationReason(playerUUID, hasLeaves) : null;
        if (reason != null) {
            confirmations.setPendingConfirmation(
                    playerUUID,
                    reason,
                    originalBlock.getLocation().clone(),
                    tool == null ? null : tool.clone(),
                    hasLeaves);
            String messageKey =
                    switch (reason) {
                        case FLOATING -> MessageKeys.CONFIRMATION_REQUIRED_FLOATING;
                        case IDLE_OR_REJOIN -> MessageKeys.CONFIRMATION_REQUIRED_IDLE;
                        case NO_LEAVES -> MessageKeys.CONFIRMATION_REQUIRED_NO_LEAVES;
                        case BOTH -> MessageKeys.CONFIRMATION_REQUIRED_BOTH;
                    };
            AutoTreeChop.sendMessage(
                    player,
                    messageKey,
                    Placeholder.parsed("timeout", String.valueOf(config.getConfirmationWindowSeconds())));
            sessionManager.clearTreeChopSession(playerUUID);
            return;
        }
        confirmations.recordSuccessfulChop(playerUUID, confirmedReason, hasLeaves);
        if (confirmedReason != null) AutoTreeChop.sendMessage(player, MessageKeys.CONFIRMATION_SUCCESS);
        sessionManager.addTreeChopLocations(playerUUID, treeBlocks);

        executeTreeChop(
                treeBlocks,
                player,
                tool,
                config,
                playerConfig,
                hooks,
                originalBlock,
                grounding.grounded() ? grounding.plantableBases() : Set.of());
    }

    private void executeTreeChop(
            Set<Location> treeBlocks,
            Player player,
            ItemStack tool,
            Config config,
            PlayerConfig playerConfig,
            ProtectionCheckUtils.ProtectionHooks hooks,
            Block originalBlock,
            Set<Location> plantableBases) {

        List<Location> blockList = new ArrayList<>(treeBlocks);
        int batchSize = config.getChopBatchSize();
        int toolSlot = player.getInventory().getHeldItemSlot();
        ItemStack[] expectedTool = {tool};
        boolean[] stopped = {false};
        UUID playerUUID = player.getUniqueId();

        Location centerLocation = originalBlock.getLocation().clone();
        Map<Material, Location> logTypesForReplant = new HashMap<>();
        Set<Location> actuallyRemovedLogs = ConcurrentHashMap.newKeySet();

        BlockSnapshot leafSnapshot = null;
        if (config.isLeafRemovalEnabled() && player.hasPermission("autotreechop.leaves")) {
            try {
                leafSnapshot =
                        BlockSnapshotCreator.captureLeafRegion(treeBlocks, config.getLeafRemovalRadius(), config);
            } catch (Exception e) {
                plugin.getLogger().warning("Error pre-capturing leaf snapshot: " + e.getMessage());
            }
            if (leafSnapshot == null) {
                sessionManager.removeTreeChopLocations(playerUUID, blockList);
                AutoTreeChop.sendMessage(player, MessageKeys.TREE_SCAN_INCOMPLETE);
                return;
            }
        }

        if (config.isVisualEffect()) EffectUtils.showChopEffect(player, originalBlock);
        BlockSnapshot finalLeafSnapshot = leafSnapshot;

        batchProcessor.processBatch(
                blockList,
                0,
                batchSize,
                (location, index) -> {
                    if (stopped[0]) return;
                    if (!RegionAccess.owns(location)) {
                        stopped[0] = true;
                        return;
                    }
                    if (!isCurrentPlayer(player, playerConfig)
                            || player.getInventory().getHeldItemSlot() != toolSlot
                            || !Objects.equals(
                                    expectedTool[0], player.getInventory().getItemInMainHand())) {
                        stopped[0] = true;
                        return;
                    }
                    if (actuallyRemovedLogs.isEmpty()
                            && (plugin.getCooldownManager().isInCooldown(playerUUID)
                                    || !PermissionUtils.canUse(player, playerConfig, config))) return;
                    if (!PermissionUtils.canBreakBlocks(player, playerConfig, config, 1)) return;
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
                    boolean dropItems = true;

                    if (config.isCallBlockBreakEvent()) {
                        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
                        plugin.getServer().getPluginManager().callEvent(breakEvent);
                        if (breakEvent.isCancelled()) {
                            return;
                        }
                        dropItems = breakEvent.isDropItems();
                    }

                    if (config.getPlayBreakSound()) {
                        XSound.BLOCK_WOOD_BREAK.play(location, 1.0f, 1.0f);
                    }
                    // Event listeners may change the player inventory or disconnect them.
                    if (!isCurrentPlayer(player, playerConfig)
                            || player.getInventory().getHeldItemSlot() != toolSlot
                            || !Objects.equals(
                                    expectedTool[0], player.getInventory().getItemInMainHand())) {
                        stopped[0] = true;
                        return;
                    }
                    if (!PermissionUtils.canBreakBlocks(player, playerConfig, config, 1)) return;
                    ItemStack heldTool = player.getInventory().getItemInMainHand();
                    if (block.getType() != originalLogType) return;
                    if (dropItems) {
                        if (!block.breakNaturally()) return;
                    } else {
                        block.setType(Material.AIR, false);
                    }
                    if (actuallyRemovedLogs.isEmpty()) {
                        playerConfig.incrementDailyUses();
                        plugin.getCooldownManager().setCooldown(player, playerUUID, config);
                    }
                    // Charge on the same tick as each removal, before the player can swap items.
                    if (config.isToolDamage()) {
                        applyToolDamage(player, heldTool, 1, config);
                        expectedTool[0] = heldTool.clone();
                        if (heldTool.getAmount() == 0) stopped[0] = true;
                    }

                    // Replant only at a removed base that already touched soil before chopping.
                    Material replantType = BlockDiscoveryUtils.treeFamily(originalLogType);
                    Location existingLoc = logTypesForReplant.get(replantType);
                    if (plantableBases.contains(location)
                            && (existingLoc == null || location.getBlockY() < existingLoc.getBlockY())) {
                        logTypesForReplant.put(replantType, location.clone());
                    }

                    actuallyRemovedLogs.add(location);
                    sessionManager.trackRemovedLogForPlayer(playerUUID.toString(), location);
                    playerConfig.incrementDailyBlocksBroken();
                },
                () -> {
                    if (plugin.getDataManager().getPlayerConfig(playerUUID) != playerConfig) return;
                    sessionManager.removeTreeChopLocations(playerUUID, blockList);
                    if (!isCurrentPlayer(player, playerConfig)) return;
                    if (actuallyRemovedLogs.isEmpty()) return;
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
                            if (!RegionAccess.owns(entry.getValue())) continue;
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
                                    actuallyRemovedLogs,
                                    plantableBases,
                                    treeBlocks);
                        }
                    }

                    plugin.getCooldownManager().setCooldown(player, playerUUID, config);
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

        if (!isCurrentPlayer(player, playerConfig)) return;

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

        if (!isCurrentPlayer(player, playerConfig) || leavesToRemove.isEmpty()) {
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
                    if (!isCurrentPlayer(player, playerConfig) || !RegionAccess.owns(location)) return false;
                    if (config.getLeafRemovalCountsTowardsLimit()) {
                        if (!PermissionUtils.canBreakBlocks(player, playerConfig, config, 1)) {
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

            Material originalLeafType = leafBlock.getType();
            boolean dropItems = config.getLeafRemovalDropItems();
            if (config.isCallBlockBreakEvent()) {
                BlockBreakEvent breakEvent = new BlockBreakEvent(leafBlock, player);
                plugin.getServer().getPluginManager().callEvent(breakEvent);
                if (breakEvent.isCancelled()) {
                    return false;
                }
                dropItems &= breakEvent.isDropItems();
            }

            if (!isCurrentPlayer(player, playerConfig) || leafBlock.getType() != originalLeafType) return false;
            if (config.getLeafRemovalCountsTowardsLimit()
                    && !PermissionUtils.canBreakBlocks(player, playerConfig, config, 1)) return false;

            if (config.getLeafRemovalVisualEffects()) {
                EffectUtils.showLeafRemovalEffect(player, leafBlock);
            }

            if (dropItems) {
                if (!leafBlock.breakNaturally()) return false;
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
