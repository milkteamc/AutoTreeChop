package org.milkteamc.autotreechop.spigot.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.milkteamc.autotreechop.spigot.AutoTreeChop;
import org.milkteamc.autotreechop.spigot.Config;
import org.milkteamc.autotreechop.spigot.PlayerConfig;
import org.milkteamc.autotreechop.spigot.hooks.GriefPreventionHook;
import org.milkteamc.autotreechop.spigot.hooks.LandsHook;
import org.milkteamc.autotreechop.spigot.hooks.ResidenceHook;
import org.milkteamc.autotreechop.spigot.hooks.WorldGuardHook;

import java.util.Set;

import static org.milkteamc.autotreechop.spigot.AutoTreeChop.sendMessage;

public class TreeChopUtils {

    public static void chopTree(Block block, Player player, boolean ConnectedBlocks, Location location, Material material, BlockData blockData, AutoTreeChop plugin, Set<Location> processingLocations, Set<Location> checkedLocations, Config config, PlayerConfig playerConfig, boolean worldGuardEnabled, boolean residenceEnabled, boolean griefPreventionEnabled, boolean landsEnabled, LandsHook landsHook, ResidenceHook residenceHook, GriefPreventionHook griefPreventionHook, WorldGuardHook worldGuardHook) {
        // Permission checks
        if (!resCheck(player, location, residenceEnabled, residenceHook) || !landsCheck(player, location, landsEnabled, landsHook) ||
                !gfCheck(player, location, griefPreventionEnabled, griefPreventionHook) || !wgCheck(player, location, worldGuardEnabled, worldGuardHook)) {
            return;
        }
        // Skip if already checked or being processed
        if (checkedLocations.contains(block.getLocation()) ||
                processingLocations.contains(block.getLocation())) {
            return;
        }

        checkedLocations.add(block.getLocation());

        if (!isLog(block.getType(), config)) {
            return;
        }

        if (config.getMustUseTool() && !isTool(player)) {
            return;
        }

        // Add to processing set to prevent recursion
        processingLocations.add(block.getLocation());

        // Call BlockBreakEvent for this block
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(breakEvent);

        if (!breakEvent.isCancelled()) {
            // Break the block and update player stats
            block.breakNaturally();
            playerConfig.incrementDailyBlocksBroken();
            if (config.isToolDamage()) {
                damageTool(player, config.getToolDamageDecrease());
            }

            // Process adjacent blocks
            Runnable task = () -> {
                for (int yOffset = -1; yOffset <= 1; yOffset++) {
                    for (int xOffset = -1; xOffset <= 1; xOffset++) {
                        for (int zOffset = -1; zOffset <= 1; zOffset++) {
                            if (xOffset == 0 && yOffset == 0 && zOffset == 0) continue;

                            Block relativeBlock = block.getRelative(xOffset, yOffset, zOffset);

                            if (config.isStopChoppingIfDifferentTypes() && notSameType(block.getType(), relativeBlock.getType())) {
                                continue;
                            }
                            if (ConnectedBlocks && blockNotConnected(block, relativeBlock)) {
                                continue;
                            }

                            // Check limits before processing next block
                            if (!PermissionUtils.hasVipUses(player, playerConfig, config) && playerConfig.getDailyUses() >= config.getMaxUsesPerDay()) {
                                sendMessage(player, AutoTreeChop.HIT_MAX_USAGE_MESSAGE);
                                return;
                            }
                            if (!PermissionUtils.hasVipBlock(player, playerConfig, config) && playerConfig.getDailyBlocksBroken() >= config.getMaxBlocksPerDay()) {
                                sendMessage(player, AutoTreeChop.HIT_MAX_BLOCK_MESSAGE);
                                return;
                            }

                            // Schedule next block processing
                            if (AutoTreeChop.isFolia()) {
                                plugin.getServer().getRegionScheduler().run(plugin, relativeBlock.getLocation(),
                                        (task2) -> chopTree(relativeBlock, player, ConnectedBlocks, location, material, blockData, plugin, processingLocations, checkedLocations, config, playerConfig, worldGuardEnabled, residenceEnabled, griefPreventionEnabled, landsEnabled, landsHook, residenceHook, griefPreventionHook, worldGuardHook));
                            } else {
                                if (config.isChopTreeAsync()) {
                                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                                            Bukkit.getScheduler().runTask(plugin, () ->
                                                    chopTree(relativeBlock, player, ConnectedBlocks, location, material, blockData, plugin, processingLocations, checkedLocations, config, playerConfig, worldGuardEnabled, residenceEnabled, griefPreventionEnabled, landsEnabled, landsHook, residenceHook, griefPreventionHook, worldGuardHook)));
                                } else {
                                    Bukkit.getScheduler().runTask(plugin, () ->
                                            chopTree(relativeBlock, player, ConnectedBlocks, location, material, blockData, plugin, processingLocations, checkedLocations, config, playerConfig, worldGuardEnabled, residenceEnabled, griefPreventionEnabled, landsEnabled, landsHook, residenceHook, griefPreventionHook, worldGuardHook));
                                }
                            }
                        }
                    }
                }
                // Remove from processing set after all adjacent blocks are handled
                processingLocations.remove(block.getLocation());
            };

            // Execute the task based on configuration and environment
            if (!AutoTreeChop.isFolia() && config.isChopTreeAsync()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            } else {
                task.run();
            }
        } else {
            processingLocations.remove(block.getLocation());
        }
    }

    // Method to reduce the durability value of tools
    private static void damageTool(Player player, int amount) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType().getMaxDurability() > 0) {
            int newDurability = tool.getDurability() + amount;
            if (newDurability > tool.getType().getMaxDurability()) {
                player.getInventory().setItemInMainHand(null); // Remove the item if it breaks
            } else {
                tool.setDurability((short) newDurability);
            }
        }
    }

    // Check if player have Lands permission in this area
    // It will return true if player have permission, and vice versa.
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

    // Check if the two blocks are adjacent to each other.
    private static boolean blockNotConnected(Block block1, Block block2) {
        if (block1.getX() == block2.getX() && block1.getY() == block2.getY() && Math.abs(block1.getZ() - block2.getZ()) == 1) {
            return false;
        }
        if (block1.getX() == block2.getX() && Math.abs(block1.getY() - block2.getY()) == 1 && block1.getZ() == block2.getZ()) {
            return false;
        }
        return Math.abs(block1.getX() - block2.getX()) != 1 || block1.getY() != block2.getY() || block1.getZ() != block2.getZ();
    }

    // Check if player have Residence permission in this area
    // It will return true if player have permission, and vice versa.
    private static boolean resCheck(Player player, Location location, boolean residenceEnabled, ResidenceHook residenceHook) {
        return !residenceEnabled || residenceHook.checkBuild(player, location);
    }

    // Add a new method to check if two block types are the same
    private static boolean notSameType(Material type1, Material type2) {
        return type1 != type2;
    }

    public static boolean isLog(Material material, Config config) {
        return config.getLogTypes().contains(material);  // Use the getter
    }

    // Check if the item on player main hand is tool.
    public static boolean isTool(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        Material material = item.getType();

        // Check for axes
        if (material.toString().endsWith("_AXE")) {
            return true;
        }

        // Check for hoes
        if (material.toString().endsWith("_HOE")) {
            return true;
        }

        // Check for pickaxes
        if (material.toString().endsWith("_PICKAXE")) {
            return true;
        }

        // Check for shovels
        if (material.toString().endsWith("_SHOVEL")) {
            return true;
        }

        // Check for swords (some consider them tools)
        if (material.toString().endsWith("_SWORD")) {
            return true;
        }

        // Check for shears
        if(material == Material.SHEARS){
            return true;
        }

        //Check for fishing rod
        if(material == Material.FISHING_ROD){
            return true;
        }

        //Check for flint and steel
        if(material == Material.FLINT_AND_STEEL){
            return true;
        }

        return false;
    }
}