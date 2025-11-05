package org.milkteamc.autotreechop.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.enchantments.Enchantment;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.PlayerConfig;

import java.util.*;

import static org.milkteamc.autotreechop.AutoTreeChop.sendMessage;

public class TreeChopUtils {

    private static final Random random = new Random();

    private final AutoTreeChop plugin;
    private final AsyncTaskScheduler scheduler;
    private final BatchProcessor batchProcessor;
    private final SessionManager sessionManager;

    public TreeChopUtils(AutoTreeChop plugin) {
        this.plugin = plugin;
        this.scheduler = new AsyncTaskScheduler(plugin);
        this.batchProcessor = new BatchProcessor(plugin, scheduler);
        this.sessionManager = SessionManager.getInstance();
    }

    public void chopTree(Block block, Player player, boolean connectedBlocks, ItemStack tool,
                         Location location, Config config, PlayerConfig playerConfig,
                         ProtectionCheckUtils.ProtectionHooks hooks) {

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

        Runnable discoveryTask = () -> {
            Set<Location> treeBlocks = BlockDiscoveryUtils.discoverTreeBFS(
                    block, config, connectedBlocks, player, hooks
            );

            Runnable validationTask = () ->
                    validateAndExecuteChop(treeBlocks, block, player, tool, config,
                            playerConfig, hooks);

            scheduler.runTask(location, validationTask);
        };

        sessionManager.addTreeChopLocations(playerUUID, Collections.singleton(block.getLocation()));

        scheduler.runTaskAsync(config, discoveryTask);
    }

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

    private void executeTreeChop(Set<Location> treeBlocks, Player player, ItemStack tool,
                                 Config config, PlayerConfig playerConfig,
                                 ProtectionCheckUtils.ProtectionHooks hooks,
                                 Block originalBlock) {

        List<Location> blockList = new ArrayList<>(treeBlocks);
        int batchSize = config.getChopBatchSize();
        int totalBlocks = blockList.size();
        UUID playerUUID = player.getUniqueId();

        Map<Material, Location> logTypesForReplant = new HashMap<>();

        batchProcessor.processBatch(
                blockList,
                0,
                batchSize,
                (location, index) -> {
                    Block block = location.getBlock();

                    if (!BlockDiscoveryUtils.isLog(block.getType(), config)) {
                        return;
                    }

                    Material originalLogType = block.getType();
                    logTypesForReplant.putIfAbsent(originalLogType, location);

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

                    sessionManager.trackRemovedLogForPlayer(playerUUID.toString(), location);
                    playerConfig.incrementDailyBlocksBroken();
                },
                () -> {
                    if (config.isToolDamage()) {
                        applyToolDamage(tool, player, totalBlocks, config);
                    }

                    if (config.isLeafRemovalEnabled()) {
                        long delay = config.getLeafRemovalDelayTicks();
                        Runnable leafTask = () -> {
                            LeafRemovalUtils leafUtils = new LeafRemovalUtils(plugin);
                            leafUtils.processLeafRemoval(originalBlock, player, config, playerConfig, hooks);
                        };

                        if (delay > 0) {
                            scheduler.runTaskLater(originalBlock.getLocation(), leafTask, delay);
                        } else {
                            leafTask.run();
                        }
                    }

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
            return item.getEnchantmentLevel(Enchantment.DURABILITY);
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
}