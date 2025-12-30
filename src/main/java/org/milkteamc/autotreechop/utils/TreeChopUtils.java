package org.milkteamc.autotreechop.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.PlayerConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.milkteamc.autotreechop.AutoTreeChop.sendMessage;

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

    private static boolean hasEnoughDurability(ItemStack tool, int blockCount, Config config) {
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

        int estimatedDamage;
        if (config.getRespectUnbreaking() && unbreakingLevel > 0) {
            estimatedDamage = blockCount / (unbreakingLevel + 1);
        } else {
            estimatedDamage = blockCount * config.getToolDamageDecrease();
        }

        return remainingDurability > estimatedDamage;
    }

    private static void applyToolDamage(ItemStack tool, Player player, int blocksBroken, Config config) {
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

        int currentDamage = damageableMeta.getDamage();
        int newDamage = currentDamage + damageToApply;

        if (newDamage >= tool.getType().getMaxDurability()) {
            player.getInventory().removeItem(tool);
        } else {
            damageableMeta.setDamage(newDamage);
            tool.setItemMeta(damageableMeta);
        }
    }

    private static int getUnbreakingLevel(ItemStack item) {
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            return item.getEnchantmentLevel(Enchantment.UNBREAKING);
        }
        return 0;
    }

    private static boolean shouldApplyDurabilityLoss(int unbreakingLevel, Config config) {
        if (unbreakingLevel <= 0 || !config.getRespectUnbreaking()) {
            return true;
        }
        return random.nextInt(100) < (100.0 / (unbreakingLevel + 1));
    }

    public static boolean isTool(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        String materialName = item.getType().toString();

        return materialName.endsWith("_AXE") ||
                materialName.endsWith("_HOE") ||
                materialName.endsWith("_PICKAXE") ||
                materialName.endsWith("_SHOVEL") ||
                materialName.endsWith("_SWORD") ||
                item.getType() == Material.SHEARS ||
                item.getType() == Material.FISHING_ROD ||
                item.getType() == Material.FLINT_AND_STEEL;
    }

    /**
     * Main entry point for tree chopping
     * PHASE 1: Synchronous snapshot creation
     */
    public void chopTree(Block block, Player player, boolean connectedBlocks, ItemStack tool,
                         Location location, Config config, PlayerConfig playerConfig,
                         ProtectionCheckUtils.ProtectionHooks hooks) {

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

        // PHASE 1: Synchronous - Capture block snapshot
        try {
            BlockSnapshot treeSnapshot = BlockSnapshotCreator.captureTreeRegion(
                    block,
                    config,
                    connectedBlocks,
                    config.getMaxDiscoveryBlocks()
            );

            Location startLocation = block.getLocation().clone();

            // PHASE 2: Asynchronous - Calculate tree structure
            Runnable asyncDiscovery = () -> {
                try {
                    Set<Location> treeBlocks = BlockDiscoveryUtils.discoverTreeBFS(
                            treeSnapshot,
                            startLocation,
                            config,
                            connectedBlocks,
                            config.getMaxTreeSize()
                    );

                    // PHASE 3: Back to sync for validation and execution
                    Runnable validationTask = () ->
                            validateAndExecuteChop(treeBlocks, block, player, tool, config,
                                    playerConfig, hooks);

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

    /**
     * Validate tree and execute chopping
     * This runs synchronously on the region thread
     */
    private void validateAndExecuteChop(Set<Location> treeBlocks, Block originalBlock,
                                        Player player, ItemStack tool, Config config,
                                        PlayerConfig playerConfig,
                                        ProtectionCheckUtils.ProtectionHooks hooks) {

        UUID playerUUID = player.getUniqueId();

        // Validation checks
        if (treeBlocks.isEmpty()) {
            sessionManager.clearTreeChopSession(playerUUID);
            return;
        }

        if (treeBlocks.size() > config.getMaxTreeSize()) {
            sendMessage(player, AutoTreeChop.HIT_MAX_BLOCK_MESSAGE);
            sessionManager.clearTreeChopSession(playerUUID);
            return;
        }

        if (!PermissionUtils.hasVipBlock(player, playerConfig, config)) {
            if (playerConfig.getDailyBlocksBroken() + treeBlocks.size() > config.getMaxBlocksPerDay()) {
                sendMessage(player, AutoTreeChop.HIT_MAX_BLOCK_MESSAGE);
                sessionManager.clearTreeChopSession(playerUUID);
                return;
            }
        }

        if (config.isToolDamage() && !hasEnoughDurability(tool, treeBlocks.size(), config)) {
            sessionManager.clearTreeChopSession(playerUUID);
            return;
        }

        playerConfig.incrementDailyUses();

        sessionManager.addTreeChopLocations(playerUUID, treeBlocks);

        executeTreeChop(treeBlocks, player, tool, config, playerConfig, hooks, originalBlock);
    }

    /**
     * Execute tree chopping in batches
     * This runs synchronously with batch processing
     */
    private void executeTreeChop(Set<Location> treeBlocks, Player player, ItemStack tool,
                                 Config config, PlayerConfig playerConfig,
                                 ProtectionCheckUtils.ProtectionHooks hooks,
                                 Block originalBlock) {

        List<Location> blockList = new ArrayList<>(treeBlocks);
        int batchSize = config.getChopBatchSize();
        int totalBlocks = blockList.size();
        UUID playerUUID = player.getUniqueId();

        // Track the LOWEST log of each type for replanting (Y coordinate)
        Map<Material, Location> logTypesForReplant = new HashMap<>();
        // Use thread-safe set for actuallyRemovedLogs since it's accessed across batches
        Set<Location> actuallyRemovedLogs = ConcurrentHashMap.newKeySet();

        // CRITICAL: Capture leaf snapshot BEFORE removing logs
        // This ensures we can see which logs exist for proper leaf orphan detection
        BlockSnapshot leafSnapshot = null;
        if (config.isLeafRemovalEnabled()) {
            try {
                leafSnapshot = BlockSnapshotCreator.captureLeafRegion(
                        originalBlock,
                        config.getLeafRemovalRadius(),
                        config
                );
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

                    // Re-check block type (may have changed)
                    if (!BlockDiscoveryUtils.isLog(block.getType(), config)) {
                        return;
                    }

                    // Re-check protection at execution time
                    if (!ProtectionCheckUtils.canModifyBlock(player, location, hooks)) {
                        return;
                    }

                    Material originalLogType = block.getType();

                    // Track the lowest Y coordinate log for each type (for proper replanting)
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
                        block.getWorld().playSound(location, org.bukkit.Sound.BLOCK_WOOD_BREAK, 1.0f, 1.0f);
                    }
                    block.breakNaturally();

                    actuallyRemovedLogs.add(location);
                    sessionManager.trackRemovedLogForPlayer(playerUUID.toString(), location);
                    playerConfig.incrementDailyBlocksBroken();
                },
                () -> {
                    // After all logs are removed
                    if (config.isToolDamage()) {
                        applyToolDamage(tool, player, totalBlocks, config);
                    }

                    // Handle leaf removal
                    if (config.isLeafRemovalEnabled() && finalLeafSnapshot != null) {
                        long delay = config.getLeafRemovalDelayTicks();
                        Location leafProcessLocation = originalBlock.getLocation();

                        Runnable leafTask = () -> {
                            processLeafRemovalWithPreCapturedSnapshot(
                                    finalLeafSnapshot,
                                    originalBlock.getLocation(),
                                    player,
                                    config,
                                    playerConfig,
                                    hooks,
                                    actuallyRemovedLogs
                            );
                        };

                        scheduler.scheduleDelayed(leafProcessLocation, leafTask, delay);
                    }

                    // Handle replanting
                    if (TreeReplantUtils.isReplantEnabledForPlayer(player, config)) {
                        for (Map.Entry<Material, Location> entry : logTypesForReplant.entrySet()) {
                            Block blockToReplant = entry.getValue().getBlock();
                            TreeReplantUtils.scheduleReplant(
                                    player, blockToReplant, entry.getKey(), plugin, config,
                                    hooks.worldGuardEnabled, hooks.residenceEnabled,
                                    hooks.griefPreventionEnabled, hooks.landsEnabled,
                                    hooks.lands, hooks.residence, hooks.griefPrevention, hooks.worldGuard
                            );
                        }
                    }

                    plugin.getCooldownManager().setCooldown(player, playerUUID, config);

                    sessionManager.removeTreeChopLocations(playerUUID, blockList);
                }
        );
    }

    /**
     * Process leaf removal with PRE-CAPTURED snapshot
     * The snapshot was taken BEFORE logs were removed
     * PHASE 1: Already done (snapshot captured before log removal)
     * PHASE 2: Async - Calculate leaves to remove
     * PHASE 3: Sync - Remove leaves in batches
     */
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

        // Check if player already has an active leaf removal session
        if (sessionManager.hasActiveLeafRemovalSession(playerKey)) {
            return;
        }

        // Start a new session
        String sessionId = sessionManager.startLeafRemovalSession(playerKey);
        if (sessionId == null) {
            return;
        }

        // PHASE 2: Asynchronous - Calculate leaves to remove
        Runnable asyncLeafCalculation = () -> {
            try {
                // Use the provided removedLogs directly
                // (already contains all actually removed logs from executeTreeChop)
                Set<Location> leavesToRemove;
                int radius = config.getLeafRemovalRadius();

                // Choose discovery method based on radius and mode
                if ("smart".equalsIgnoreCase(config.getLeafRemovalMode())) {
                    leavesToRemove = BlockDiscoveryUtils.discoverLeavesBFS(
                            leafSnapshot,
                            centerLocation,
                            radius,
                            config,
                            removedLogs
                    );
                } else {
                    // For "aggressive" or "radius" mode, radial is faster
                    leavesToRemove = BlockDiscoveryUtils.discoverLeavesRadial(
                            leafSnapshot,
                            centerLocation,
                            radius,
                            config,
                            removedLogs
                    );
                }

                // PHASE 3: Back to sync for removal
                Runnable removalTask = () ->
                        executeLeafRemoval(leavesToRemove, player, config, playerConfig,
                                hooks, sessionId, playerKey);

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
            // Run synchronously if async is disabled
            asyncLeafCalculation.run();
        }
    }

    /**
     * Execute leaf removal in batches
     * This runs synchronously on the region thread
     */
    private void executeLeafRemoval(Set<Location> leavesToRemove, Player player,
                                    Config config, PlayerConfig playerConfig,
                                    ProtectionCheckUtils.ProtectionHooks hooks,
                                    String sessionId, String playerKey) {

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
                    // Check daily limit if counting towards limit
                    if (config.getLeafRemovalCountsTowardsLimit()) {
                        if (!PermissionUtils.hasVipBlock(player, playerConfig, config) &&
                                playerConfig.getDailyBlocksBroken() >= config.getMaxBlocksPerDay()) {
                            return false; // Stop processing - limit reached
                        }
                    }

                    Block leafBlock = location.getBlock();

                    // Remove the leaf block with all checks
                    removeLeafBlock(leafBlock, player, config, playerConfig, hooks);

                    return true; // Continue processing
                },
                () -> {
                    // Leaf removal complete - end session
                    sessionManager.endLeafRemovalSession(sessionId, playerKey);
                }
        );
    }

    /**
     * Remove a single leaf block with all necessary checks
     * This runs synchronously on the region thread
     */
    private boolean removeLeafBlock(Block leafBlock, Player player, Config config,
                                    PlayerConfig playerConfig,
                                    ProtectionCheckUtils.ProtectionHooks hooks) {

        Location leafLocation = leafBlock.getLocation();

        // Check if already processing this location
        if (processingLeafLocations.contains(leafLocation)) {
            return false;
        }

        // Re-check if it's still a leaf
        if (!BlockDiscoveryUtils.isLeafBlock(leafBlock.getType(), config)) {
            return false;
        }

        // Re-check protection at execution time
        if (!ProtectionCheckUtils.canModifyBlock(player, leafLocation, hooks)) {
            return false;
        }

        // Mark as processing
        processingLeafLocations.add(leafLocation);

        try {
            // Call BlockBreakEvent if enabled
            if (config.isCallBlockBreakEvent()) {
                BlockBreakEvent breakEvent = new BlockBreakEvent(leafBlock, player);
                plugin.getServer().getPluginManager().callEvent(breakEvent);
                if (breakEvent.isCancelled()) {
                    return false;
                }
            }

            // Show visual effects
            if (config.getLeafRemovalVisualEffects()) {
                EffectUtils.showLeafRemovalEffect(player, leafBlock);
            }

            // Remove the block
            if (config.getLeafRemovalDropItems()) {
                leafBlock.breakNaturally();
            } else {
                leafBlock.setType(Material.AIR, false);
            }

            // Update daily blocks count if needed
            if (config.getLeafRemovalCountsTowardsLimit()) {
                playerConfig.incrementDailyBlocksBroken();
            }

            return true;

        } finally {
            // Always remove from processing set
            processingLeafLocations.remove(leafLocation);
        }
    }
}