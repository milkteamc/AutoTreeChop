package org.milkteamc.autotreechop.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.jetbrains.annotations.NotNull;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.PlayerConfig;
import org.milkteamc.autotreechop.hooks.GriefPreventionHook;
import org.milkteamc.autotreechop.hooks.LandsHook;
import org.milkteamc.autotreechop.hooks.ResidenceHook;
import org.milkteamc.autotreechop.hooks.WorldGuardHook;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.milkteamc.autotreechop.AutoTreeChop.sendMessage;

public class TreeChopUtils {

    private static final Random random = new Random();

    public static void chopTree(Block block, Player player, boolean connectedBlocks, ItemStack tool,
                                Location location, Material material, BlockData blockData, AutoTreeChop plugin,
                                Set<Location> processingLocations, Set<Location> checkedLocations, Config config,
                                PlayerConfig playerConfig, boolean worldGuardEnabled, boolean residenceEnabled,
                                boolean griefPreventionEnabled, boolean landsEnabled, LandsHook landsHook,
                                ResidenceHook residenceHook, GriefPreventionHook griefPreventionHook,
                                WorldGuardHook worldGuardHook) {

        if (!resCheck(player, location, residenceEnabled, residenceHook) ||
                !landsCheck(player, location, landsEnabled, landsHook) ||
                !gfCheck(player, location, griefPreventionEnabled, griefPreventionHook) ||
                !wgCheck(player, location, worldGuardEnabled, worldGuardHook)) {
            return;
        }

        if (processingLocations.contains(block.getLocation())) {
            return;
        }

        if (!isLog(block.getType(), config)) {
            return;
        }

        if (config.getMustUseTool() && !isTool(player)) {
            return;
        }

        processingLocations.add(block.getLocation());

        Runnable discoveryTask = () -> {
            Set<Location> treeBlocks = new HashSet<>();
            discoverTree(block, treeBlocks, config, connectedBlocks,
                    player, worldGuardEnabled, residenceEnabled, griefPreventionEnabled,
                    landsEnabled, landsHook, residenceHook, griefPreventionHook, worldGuardHook);

            Runnable validationTask = () -> {
                if (treeBlocks.isEmpty()) {
                    processingLocations.remove(block.getLocation());
                    return;
                }

                if (treeBlocks.size() > config.getMaxTreeSize()) {
                    sendMessage(player, AutoTreeChop.HIT_MAX_BLOCK_MESSAGE);
                    processingLocations.remove(block.getLocation());
                    return;
                }

                if (!PermissionUtils.hasVipBlock(player, playerConfig, config)) {
                    if (playerConfig.getDailyBlocksBroken() + treeBlocks.size() > config.getMaxBlocksPerDay()) {
                        sendMessage(player, AutoTreeChop.HIT_MAX_BLOCK_MESSAGE);
                        processingLocations.remove(block.getLocation());
                        return;
                    }
                }

                if (config.isToolDamage() && !hasEnoughDurability(tool, treeBlocks.size(), config)) {
                    processingLocations.remove(block.getLocation());
                    return;
                }

                playerConfig.incrementDailyUses();
                // REMOVED: cooldown set here - moved to end of processing

                processingLocations.addAll(treeBlocks);

                executeTreeChop(treeBlocks, player, tool, plugin, config, playerConfig,
                        processingLocations, worldGuardEnabled, residenceEnabled,
                        griefPreventionEnabled, landsEnabled, landsHook, residenceHook,
                        griefPreventionHook, worldGuardHook, block);
            };

            if (AutoTreeChop.isFolia()) {
                plugin.getServer().getRegionScheduler().run(plugin, location, task -> validationTask.run());
            } else {
                Bukkit.getScheduler().runTask(plugin, validationTask);
            }
        };

        if (config.isChopTreeAsync() && !AutoTreeChop.isFolia()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, discoveryTask);
        } else {
            discoveryTask.run();
        }
    }

    private static void discoverTree(Block startBlock, Set<Location> treeBlocks, Config config,
                                     boolean connectedBlocks, Player player, boolean worldGuardEnabled,
                                     boolean residenceEnabled, boolean griefPreventionEnabled,
                                     boolean landsEnabled, LandsHook landsHook, ResidenceHook residenceHook,
                                     GriefPreventionHook griefPreventionHook, WorldGuardHook worldGuardHook) {

        Queue<Block> toCheck = new LinkedList<>();
        Set<Location> checked = new HashSet<>();
        toCheck.add(startBlock);
        checked.add(startBlock.getLocation());

        Material originalType = startBlock.getType();
        int maxBlocks = config.getMaxDiscoveryBlocks();

        while (!toCheck.isEmpty() && treeBlocks.size() < maxBlocks) {
            Block current = toCheck.poll();

            // Permission check for this block
            Location loc = current.getLocation();
            if (!resCheck(player, loc, residenceEnabled, residenceHook) ||
                    !landsCheck(player, loc, landsEnabled, landsHook) ||
                    !gfCheck(player, loc, griefPreventionEnabled, griefPreventionHook) ||
                    !wgCheck(player, loc, worldGuardEnabled, worldGuardHook)) {
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

            // Add to tree blocks
            treeBlocks.add(current.getLocation());

            // Check adjacent blocks
            for (int yOffset = -1; yOffset <= 1; yOffset++) {
                for (int xOffset = -1; xOffset <= 1; xOffset++) {
                    for (int zOffset = -1; zOffset <= 1; zOffset++) {
                        if (xOffset == 0 && yOffset == 0 && zOffset == 0) continue;

                        Block relative = current.getRelative(xOffset, yOffset, zOffset);
                        Location relativeLoc = relative.getLocation();

                        if (checked.contains(relativeLoc)) continue;
                        checked.add(relativeLoc);

                        // Check connectivity if required
                        if (connectedBlocks && blockNotConnected(current, relative)) {
                            continue;
                        }

                        toCheck.add(relative);
                    }
                }
            }
        }
    }

    /**
     * Phase 3: Execute the actual tree chopping with batching
     */
    private static void executeTreeChop(Set<Location> treeBlocks, Player player, ItemStack tool,
                                        AutoTreeChop plugin, Config config, PlayerConfig playerConfig,
                                        Set<Location> processingLocations, boolean worldGuardEnabled,
                                        boolean residenceEnabled, boolean griefPreventionEnabled,
                                        boolean landsEnabled, LandsHook landsHook, ResidenceHook residenceHook,
                                        GriefPreventionHook griefPreventionHook, WorldGuardHook worldGuardHook,
                                        Block originalBlock) {

        List<Location> blockList = new ArrayList<>(treeBlocks);
        int batchSize = config.getChopBatchSize();
        int totalBlocks = blockList.size();

        Map<Material, Location> logTypesForReplant = new HashMap<>();

        // Process in batches
        processNextBatch(blockList, 0, batchSize, player, tool, plugin, config, playerConfig,
                processingLocations, worldGuardEnabled, residenceEnabled, griefPreventionEnabled,
                landsEnabled, landsHook, residenceHook, griefPreventionHook, worldGuardHook,
                totalBlocks, logTypesForReplant, originalBlock);
    }

    private static void processNextBatch(List<Location> blockList, int startIndex, int batchSize,
                                         Player player, ItemStack tool, AutoTreeChop plugin, Config config,
                                         PlayerConfig playerConfig, Set<Location> processingLocations,
                                         boolean worldGuardEnabled, boolean residenceEnabled,
                                         boolean griefPreventionEnabled, boolean landsEnabled,
                                         LandsHook landsHook, ResidenceHook residenceHook,
                                         GriefPreventionHook griefPreventionHook, WorldGuardHook worldGuardHook,
                                         int totalBlocks, Map<Material, Location> logTypesForReplant,
                                         Block originalBlock) {

        int endIndex = Math.min(startIndex + batchSize, blockList.size());
        boolean isLastBatch = endIndex >= blockList.size();

        for (int i = startIndex; i < endIndex; i++) {
            Location loc = blockList.get(i);
            Block block = loc.getBlock();

            if (!isLog(block.getType(), config)) {
                continue;
            }

            Material originalLogType = block.getType();
            logTypesForReplant.putIfAbsent(originalLogType, loc);

            if (config.isCallBlockBreakEvent()) {
                BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
                Bukkit.getPluginManager().callEvent(breakEvent);

                if (breakEvent.isCancelled()) {
                    continue;
                }
            }

            block.breakNaturally();
            LeafRemovalUtils.trackRemovedLog(loc, player.getUniqueId().toString());

            if (config.getPlayBreakSound()) {
                block.getWorld().playSound(loc, org.bukkit.Sound.BLOCK_WOOD_BREAK, 1.0f, 1.0f);
            }

            playerConfig.incrementDailyBlocksBroken();
        }

        if (isLastBatch) {
            if (config.isToolDamage()) {
                applyToolDamage(tool, player, totalBlocks, config);
            }

            LeafRemovalUtils.processLeafRemoval(originalBlock, player, plugin, config, playerConfig,
                    worldGuardEnabled, residenceEnabled, griefPreventionEnabled, landsEnabled,
                    landsHook, residenceHook, griefPreventionHook, worldGuardHook);

            if (TreeReplantUtils.isReplantEnabledForPlayer(player, config)) {
                for (Map.Entry<Material, Location> entry : logTypesForReplant.entrySet()) {
                    Block blockToReplant = entry.getValue().getBlock();
                    TreeReplantUtils.scheduleReplant(
                            player,
                            blockToReplant,
                            entry.getKey(),
                            plugin,
                            config,
                            worldGuardEnabled,
                            residenceEnabled,
                            griefPreventionEnabled,
                            landsEnabled,
                            landsHook,
                            residenceHook,
                            griefPreventionHook,
                            worldGuardHook
                    );
                }
            }

            plugin.getCooldownManager().setCooldown(player, player.getUniqueId(), config);

            blockList.forEach(processingLocations::remove);
        } else {
            Runnable nextBatchTask = () -> processNextBatch(blockList, endIndex, batchSize, player, tool,
                    plugin, config, playerConfig, processingLocations, worldGuardEnabled, residenceEnabled,
                    griefPreventionEnabled, landsEnabled, landsHook, residenceHook, griefPreventionHook,
                    worldGuardHook, totalBlocks, logTypesForReplant, originalBlock);

            if (AutoTreeChop.isFolia()) {
                plugin.getServer().getRegionScheduler().runDelayed(plugin,
                        blockList.get(endIndex), task -> nextBatchTask.run(), 1L);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, nextBatchTask, 1L);
            }
        }
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

        // Estimate damage (conservative estimate)
        int estimatedDamage;
        if (config.getRespectUnbreaking() && unbreakingLevel > 0) {
            // With unbreaking, roughly 1/(level+1) chance of damage per hit
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

        // Calculate actual damage based on unbreaking
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
            return item.getEnchantmentLevel(Enchantment.DURABILITY);
        }
        return 0;
    }

    private static boolean shouldApplyDurabilityLoss(int unbreakingLevel, Config config) {
        if (unbreakingLevel <= 0) {
            return true;
        }

        if (!config.getRespectUnbreaking()) {
            return true;
        }

        return random.nextInt(100) < (100.0 / (unbreakingLevel + 1));
    }

    public static boolean landsCheck(Player player, @NotNull Location location, boolean landsEnabled, LandsHook landsHook) {
        return !landsEnabled || landsHook.checkBuild(player, location);
    }

    public static boolean wgCheck(Player player, Location location, boolean worldGuardEnabled, WorldGuardHook worldGuardHook) {
        if (!worldGuardEnabled) {
            return true;
        }
        return worldGuardHook.checkBuild(player, location);
    }

    public static boolean gfCheck(Player player, Location location, boolean griefPreventionEnabled, GriefPreventionHook griefPreventionHook) {
        return !griefPreventionEnabled || griefPreventionHook.checkBuild(player, location);
    }

    private static boolean blockNotConnected(Block block1, Block block2) {
        if (block1.getX() == block2.getX() && block1.getY() == block2.getY() && Math.abs(block1.getZ() - block2.getZ()) == 1) {
            return false;
        }
        if (block1.getX() == block2.getX() && Math.abs(block1.getY() - block2.getY()) == 1 && block1.getZ() == block2.getZ()) {
            return false;
        }
        return Math.abs(block1.getX() - block2.getX()) != 1 || block1.getY() != block2.getY() || block1.getZ() != block2.getZ();
    }

    static boolean resCheck(Player player, Location location, boolean residenceEnabled, ResidenceHook residenceHook) {
        return !residenceEnabled || residenceHook.checkBuild(player, location);
    }

    public static boolean isLog(Material material, Config config) {
        return config.getLogTypes().contains(material);
    }

    public static boolean isTool(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        Material material = item.getType();

        if (material.toString().endsWith("_AXE")) {
            return true;
        }

        if (material.toString().endsWith("_HOE")) {
            return true;
        }

        if (material.toString().endsWith("_PICKAXE")) {
            return true;
        }

        if (material.toString().endsWith("_SHOVEL")) {
            return true;
        }

        if (material.toString().endsWith("_SWORD")) {
            return true;
        }

        if(material == Material.SHEARS){
            return true;
        }

        if(material == Material.FISHING_ROD){
            return true;
        }

        if(material == Material.FLINT_AND_STEEL){
            return true;
        }

        return false;
    }
}