package org.milkteamc.autotreechop.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.hooks.GriefPreventionHook;
import org.milkteamc.autotreechop.hooks.LandsHook;
import org.milkteamc.autotreechop.hooks.ResidenceHook;
import org.milkteamc.autotreechop.hooks.WorldGuardHook;

public class TreeReplantUtils {

    /**
     * Schedules a sapling replant at the given location after a delay
     * Called from TreeChopUtils after a log block is broken
     */
    public static void scheduleReplant(Player player, Block brokenLogBlock, Material originalLogType, AutoTreeChop plugin, Config config,
                                       boolean worldGuardEnabled, boolean residenceEnabled,
                                       boolean griefPreventionEnabled, boolean landsEnabled,
                                       LandsHook landsHook, ResidenceHook residenceHook,
                                       GriefPreventionHook griefPreventionHook, WorldGuardHook worldGuardHook) {

        // Check if auto-replant is enabled
        if (!config.isAutoReplantEnabled()) {
            return;
        }

        // Get the appropriate sapling type for this log
        Material saplingType = config.getSaplingForLog(originalLogType);
        if (saplingType == null) {
            return; // No sapling mapping found
        }

        // Only replant if this is likely the base of the tree (has soil directly below)
        Block below = brokenLogBlock.getRelative(BlockFace.DOWN);
        if (!isValidSoil(below.getType(), config)) {
            return; // Not a base log, skip replanting
        }

        // Find a suitable location to plant the sapling
        Location plantLocation = findSuitablePlantLocation(brokenLogBlock.getLocation(), config);
        if (plantLocation == null) {
            return; // No suitable location found
        }

        // Schedule the replanting task
        Runnable replantTask = () -> {
            // Double-check permissions at plant time (in case they changed)
            if (!hasReplantPermission(player, plantLocation, worldGuardEnabled, residenceEnabled,
                    griefPreventionEnabled, landsEnabled, landsHook, residenceHook,
                    griefPreventionHook, worldGuardHook)) {
                return;
            }

            // Attempt to plant the sapling
            boolean planted = plantSapling(player, plantLocation, saplingType, config);

            if (planted && config.getReplantVisualEffect()) {
                // Show visual effect for successful replanting
                EffectUtils.showReplantEffect(player, plantLocation.getBlock());
            }
        };

        // Schedule the task based on configuration and server type
        long delayTicks = config.getReplantDelayTicks();

        if (AutoTreeChop.isFolia()) {
            plugin.getServer().getRegionScheduler().runDelayed(plugin, plantLocation,
                    (task) -> replantTask.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, replantTask, delayTicks);
        }
    }

    /**
     * Finds a suitable location to plant a sapling near the original log location
     * Checks for valid soil and clear space above
     */
    private static Location findSuitablePlantLocation(Location originalLocation, Config config) {
        Block originalBlock = originalLocation.getBlock();

        // First, check if we can plant at the original location
        Block belowOriginal = originalBlock.getRelative(BlockFace.DOWN);
        if (isValidSoil(belowOriginal.getType(), config) && isClearForSapling(originalBlock)) {
            return originalLocation;
        }

        // Search in a 3x3 area around the original location
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue; // Skip center (already checked)

                Block checkBlock = originalBlock.getRelative(x, 0, z);
                Block belowCheck = checkBlock.getRelative(BlockFace.DOWN);

                if (isValidSoil(belowCheck.getType(), config) && isClearForSapling(checkBlock)) {
                    return checkBlock.getLocation();
                }
            }
        }

        return null; // No suitable location found
    }

    /**
     * Checks if a block type is valid soil for sapling planting
     */
    private static boolean isValidSoil(Material material, Config config) {
        return config.getValidSoilTypes().contains(material);
    }

    /**
     * Checks if a location is clear for sapling planting (air or replaceable blocks)
     */
    private static boolean isClearForSapling(Block block) {
        Material type = block.getType();
        return type == Material.AIR ||
                type == Material.SHORT_GRASS ||
                type == Material.TALL_GRASS ||
                type == Material.FERN ||
                type == Material.LARGE_FERN ||
                type == Material.DEAD_BUSH;
    }

    /**
     * Attempts to plant a sapling at the specified location
     * Handles inventory consumption if required
     */
    private static boolean plantSapling(Player player, Location location, Material saplingType, Config config) {
        Block block = location.getBlock();

        // Check if player needs to have sapling in inventory
        if (config.getRequireSaplingInInventory()) {
            if (!consumeSaplingFromInventory(player, saplingType)) {
                // Player doesn't have the required sapling
                return false;
            }
        }

        // Place the sapling
        block.setType(saplingType);
        return true;
    }

    /**
     * Consumes one sapling of the specified type from player's inventory
     * Returns true if successful, false if player doesn't have the sapling
     */
    private static boolean consumeSaplingFromInventory(Player player, Material saplingType) {
        PlayerInventory inventory = player.getInventory();
        ItemStack saplingStack = new ItemStack(saplingType, 1);

        if (inventory.containsAtLeast(saplingStack, 1)) {
            inventory.removeItem(saplingStack);
            return true;
        }
        return false;
    }

    /**
     * Checks if the player has permission to replant at the given location
     * Uses the same protection plugin checks as TreeChopUtils
     */
    private static boolean hasReplantPermission(Player player, Location location,
                                                boolean worldGuardEnabled, boolean residenceEnabled,
                                                boolean griefPreventionEnabled, boolean landsEnabled,
                                                LandsHook landsHook, ResidenceHook residenceHook,
                                                GriefPreventionHook griefPreventionHook,
                                                WorldGuardHook worldGuardHook) {

        // Use the same permission checks as TreeChopUtils
        return TreeChopUtils.resCheck(player, location, residenceEnabled, residenceHook) &&
                TreeChopUtils.landsCheck(player, location, landsEnabled, landsHook) &&
                TreeChopUtils.gfCheck(player, location, griefPreventionEnabled, griefPreventionHook) &&
                TreeChopUtils.wgCheck(player, location, worldGuardEnabled, worldGuardHook);
    }

    /**
     * Checks if auto-replant is enabled for the given player
     * Can be extended to add per-player replant settings
     */
    public static boolean isReplantEnabledForPlayer(Player player, Config config) {
        // Basic permission check - can be extended with per-player settings
        return config.isAutoReplantEnabled() &&
                player.hasPermission("autotreechop.replant");
    }
}