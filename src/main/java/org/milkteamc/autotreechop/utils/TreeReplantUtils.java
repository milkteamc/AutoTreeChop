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

import com.cryptomorin.xseries.XMaterial;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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

    private static final int[][] FORMATION_2X2 = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};

    public static void scheduleReplant(
            Player player,
            Block brokenLogBlock,
            Material originalLogType,
            AutoTreeChop plugin,
            Config config,
            boolean worldGuardEnabled,
            boolean residenceEnabled,
            boolean griefPreventionEnabled,
            boolean landsEnabled,
            LandsHook landsHook,
            ResidenceHook residenceHook,
            GriefPreventionHook griefPreventionHook,
            WorldGuardHook worldGuardHook,
            Set<Location> choppedLogs) {

        if (!config.isAutoReplantEnabled()) {
            return;
        }

        Material saplingType = config.getSaplingForLog(originalLogType);
        if (saplingType == null) {
            return;
        }

        Location originalLocation = brokenLogBlock.getLocation().clone();
        boolean needs2x2 = isLikely2x2Tree(originalLogType, originalLocation, choppedLogs);

        Runnable replantTask = () -> {
            if (needs2x2) {
                Location anchorLocation = find2x2PlantLocation(originalLocation, config);
                if (anchorLocation == null) {
                    return;
                }
                // Check permission at all four sapling positions
                Block anchor = anchorLocation.getBlock();
                for (int[] offset : FORMATION_2X2) {
                    Location pos = anchor.getRelative(offset[0], 0, offset[1]).getLocation();
                    if (!hasReplantPermission(
                            player,
                            pos,
                            worldGuardEnabled,
                            residenceEnabled,
                            griefPreventionEnabled,
                            landsEnabled,
                            landsHook,
                            residenceHook,
                            griefPreventionHook,
                            worldGuardHook)) {
                        return;
                    }
                }
                plant2x2Saplings(player, anchorLocation, saplingType, config);
            } else {
                Location plantLocation = findSuitablePlantLocation(originalLocation, config, plugin);
                if (plantLocation == null) {
                    return;
                }
                // Check permission at the actual plant location, not the original break point
                if (!hasReplantPermission(
                        player,
                        plantLocation,
                        worldGuardEnabled,
                        residenceEnabled,
                        griefPreventionEnabled,
                        landsEnabled,
                        landsHook,
                        residenceHook,
                        griefPreventionHook,
                        worldGuardHook)) {
                    return;
                }
                boolean planted = plantSapling(player, plantLocation, saplingType, config, plugin);
                if (planted && config.getReplantVisualEffect()) {
                    EffectUtils.showReplantEffect(player, plantLocation.getBlock());
                }
            }
        };

        long delayTicks = config.getReplantDelayTicks();

        if (AutoTreeChop.isFolia()) {
            plugin.getServer()
                    .getRegionScheduler()
                    .runDelayed(plugin, originalLocation, (task) -> replantTask.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, replantTask, delayTicks);
        }
    }

    /**
     * Determines whether the chopped tree should be replanted as a 2x2 sapling
     * formation.
     *
     * <p>Dark Oak and Pale Oak are always 2x2. Spruce and Jungle are 2x2 only when
     * the base of the chopped tree contained four logs arranged in a 2x2 square —
     * detected by scanning the chopped-log set for a matching pattern at the Y
     * level of the lowest broken log. All other tree types are always single.
     */
    private static boolean isLikely2x2Tree(Material logType, Location lowestLogLocation, Set<Location> choppedLogs) {

        XMaterial xMat = XMaterial.matchXMaterial(logType);

        // Dark Oak and Pale Oak are always planted as 2x2
        if (xMat == XMaterial.DARK_OAK_LOG || xMat == XMaterial.PALE_OAK_LOG) {
            return true;
        }

        // Only Spruce and Jungle can be big (2x2) trees — everything else is always single
        if (xMat != XMaterial.SPRUCE_LOG && xMat != XMaterial.JUNGLE_LOG) {
            return false;
        }

        // Detect 2x2 by checking whether four logs of this type form a square at
        // the base Y level among the actually-chopped blocks.
        int baseY = lowestLogLocation.getBlockY();
        int baseX = lowestLogLocation.getBlockX();
        int baseZ = lowestLogLocation.getBlockZ();
        World world = lowestLogLocation.getWorld();

        // Try all four possible 2x2 anchors that include the base-log position as a corner
        int[][] candidateAnchors = {{0, 0}, {-1, 0}, {0, -1}, {-1, -1}};
        for (int[] ao : candidateAnchors) {
            int ax = baseX + ao[0];
            int az = baseZ + ao[1];
            boolean all4Present = true;
            for (int[] offset : FORMATION_2X2) {
                if (!containsBlockLocation(choppedLogs, world, ax + offset[0], baseY, az + offset[1])) {
                    all4Present = false;
                    break;
                }
            }
            if (all4Present) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns {@code true} if {@code locations} contains a block-coordinate match
     * for the given world and integer coordinates. Uses integer comparison to avoid
     * floating-point or yaw/pitch equality issues.
     */
    private static boolean containsBlockLocation(Set<Location> locations, World world, int x, int y, int z) {
        for (Location loc : locations) {
            if (loc.getWorld() == world && loc.getBlockX() == x && loc.getBlockY() == y && loc.getBlockZ() == z) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds an anchor location where all four blocks of a 2x2 formation are
     * clear and sitting on valid soil. Returns null if no valid anchor is found.
     */
    private static Location find2x2PlantLocation(Location originalLocation, Config config) {
        Block origin = originalLocation.getBlock();

        // Try all four possible anchors that include the original position as a corner.
        int[][] originInclusiveAnchors = {{0, 0}, {-1, 0}, {0, -1}, {-1, -1}};
        for (int[] ao : originInclusiveAnchors) {
            Block anchor = origin.getRelative(ao[0], 0, ao[1]);
            if (is2x2FormationValid(anchor, config)) {
                return anchor.getLocation();
            }
        }

        // Widen search, skipping anchors already tested above
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                // Skip the four origin-inclusive anchors already tried
                if ((x == 0 || x == -1) && (z == 0 || z == -1)) continue;

                Block candidate = origin.getRelative(x, 0, z);
                if (is2x2FormationValid(candidate, config)) {
                    return candidate.getLocation();
                }
            }
        }

        return null;
    }

    /**
     * Checks whether all four blocks of a 2x2 formation starting at anchor are
     * clear and sit on valid soil.
     */
    private static boolean is2x2FormationValid(Block anchor, Config config) {
        for (int[] offset : FORMATION_2X2) {
            Block target = anchor.getRelative(offset[0], 0, offset[1]);
            Block below = target.getRelative(BlockFace.DOWN);
            if (!isClearForSapling(target) || !isValidSoil(below.getType(), config)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Plants a full 2x2 sapling formation at the given anchor location.
     *
     * <p>All four positions are re-validated before any sapling is placed. If any
     * position became occupied between the time the anchor was chosen and now (race
     * condition — another player placed a block), the entire formation is aborted
     * rather than planting a partial 2x2, which would be useless for dark oak growth.
     *
     * <p>If require-sapling-in-inventory is true, all four saplings must be present
     * before any are consumed.
     */
    private static void plant2x2Saplings(Player player, Location anchorLocation, Material saplingType, Config config) {

        Block anchor = anchorLocation.getBlock();

        for (int[] offset : FORMATION_2X2) {
            Block target = anchor.getRelative(offset[0], 0, offset[1]);
            Block below = target.getRelative(BlockFace.DOWN);
            if (!isClearForSapling(target) || !isValidSoil(below.getType(), config)) {
                return; // Abort — formation is no longer fully clear
            }
        }

        // Pre-check inventory has enough saplings before consuming any
        if (config.getRequireSaplingInInventory()) {
            int available = countSaplingsInInventory(player, saplingType);
            if (available < 4) {
                return;
            }
        }

        // All checks passed — place all four saplings
        for (int[] offset : FORMATION_2X2) {
            Block target = anchor.getRelative(offset[0], 0, offset[1]);

            if (config.getRequireSaplingInInventory()) {
                consumeSaplingFromInventory(player, saplingType);
            }

            target.setType(saplingType);

            if (config.getReplantVisualEffect()) {
                EffectUtils.showReplantEffect(player, target);
            }
        }
    }

    private static Location findSuitablePlantLocation(Location originalLocation, Config config, AutoTreeChop plugin) {
        Block originalBlock = originalLocation.getBlock();

        Block belowOriginal = originalBlock.getRelative(BlockFace.DOWN);

        if (isValidSoil(belowOriginal.getType(), config) && isClearForSapling(originalBlock)) {
            return originalLocation;
        }

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;

                Block checkBlock = originalBlock.getRelative(x, 0, z);
                Block belowCheck = checkBlock.getRelative(BlockFace.DOWN);

                if (isValidSoil(belowCheck.getType(), config) && isClearForSapling(checkBlock)) {
                    return checkBlock.getLocation();
                }
            }
        }

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) <= 1 && Math.abs(z) <= 1) continue;

                for (int yOffset = 0; yOffset >= -3; yOffset--) {
                    Block checkBlock = originalBlock.getRelative(x, yOffset, z);
                    Block belowCheck = checkBlock.getRelative(BlockFace.DOWN);

                    if (isValidSoil(belowCheck.getType(), config) && isClearForSapling(checkBlock)) {
                        return checkBlock.getLocation();
                    }
                }
            }
        }

        return null;
    }

    private static boolean isValidSoil(Material material, Config config) {
        if (config.getValidSoilTypes().contains(material)) {
            return true;
        }

        XMaterial xMat = XMaterial.matchXMaterial(material);

        return xMat == XMaterial.DIRT
                || xMat == XMaterial.GRASS_BLOCK
                || xMat == XMaterial.PODZOL
                || xMat == XMaterial.COARSE_DIRT
                || xMat == XMaterial.ROOTED_DIRT
                || xMat == XMaterial.MYCELIUM
                || xMat == XMaterial.FARMLAND
                || xMat == XMaterial.MOSS_BLOCK
                || xMat == XMaterial.MUD
                || xMat == XMaterial.MUDDY_MANGROVE_ROOTS;
    }

    private static boolean isClearForSapling(Block block) {
        Material type = block.getType();
        XMaterial xMat = XMaterial.matchXMaterial(type);

        if (xMat == XMaterial.AIR) {
            return true;
        }

        String matName = type.toString();
        if (matName.endsWith("_LOG") || matName.endsWith("_WOOD")) {
            return true;
        }

        switch (xMat) {
            case SHORT_GRASS:
            case TALL_GRASS:
            case FERN:
            case LARGE_FERN:
            case DEAD_BUSH:
            case DANDELION:
            case POPPY:
            case BLUE_ORCHID:
            case ALLIUM:
            case AZURE_BLUET:
            case RED_TULIP:
            case ORANGE_TULIP:
            case WHITE_TULIP:
            case PINK_TULIP:
            case OXEYE_DAISY:
            case CORNFLOWER:
            case LILY_OF_THE_VALLEY:
            case WITHER_ROSE:
            case SUNFLOWER:
            case LILAC:
            case ROSE_BUSH:
            case PEONY:
            case WHEAT:
            case CARROTS:
            case POTATOES:
            case BEETROOTS:
            case SWEET_BERRY_BUSH:
            case BROWN_MUSHROOM:
            case RED_MUSHROOM:
            case SUGAR_CANE:
            case VINE:
            case SNOW:
                return true;
            default:
                return matName.endsWith("_GRASS")
                        || matName.contains("FLOWER")
                        || matName.contains("SAPLING")
                        || matName.contains("LEAVES");
        }
    }

    private static boolean plantSapling(
            Player player, Location location, Material saplingType, Config config, AutoTreeChop plugin) {

        Block block = location.getBlock();
        Block below = block.getRelative(BlockFace.DOWN);

        if (!isClearForSapling(block) || !isValidSoil(below.getType(), config)) {
            return false;
        }

        if (config.getRequireSaplingInInventory() && !consumeSaplingFromInventory(player, saplingType)) {
            return false;
        }

        block.setType(saplingType);

        if (config.getReplantVisualEffect()) {
            EffectUtils.showReplantEffect(player, block);
        }

        return true;
    }

    /**
     * Returns how many of the given sapling type the player currently holds.
     */
    private static int countSaplingsInInventory(Player player, Material saplingType) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == saplingType) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private static boolean consumeSaplingFromInventory(Player player, Material saplingType) {
        PlayerInventory inventory = player.getInventory();
        ItemStack saplingStack = new ItemStack(saplingType, 1);

        if (inventory.containsAtLeast(saplingStack, 1)) {
            inventory.removeItem(saplingStack);
            return true;
        }
        return false;
    }

    private static boolean hasReplantPermission(
            Player player,
            Location location,
            boolean worldGuardEnabled,
            boolean residenceEnabled,
            boolean griefPreventionEnabled,
            boolean landsEnabled,
            LandsHook landsHook,
            ResidenceHook residenceHook,
            GriefPreventionHook griefPreventionHook,
            WorldGuardHook worldGuardHook) {

        return ProtectionCheckUtils.checkResidence(player, location, residenceEnabled, residenceHook)
                && ProtectionCheckUtils.checkLands(player, location, landsEnabled, landsHook)
                && ProtectionCheckUtils.checkGriefPrevention(
                        player, location, griefPreventionEnabled, griefPreventionHook)
                && ProtectionCheckUtils.checkWorldGuard(player, location, worldGuardEnabled, worldGuardHook);
    }

    public static boolean isReplantEnabledForPlayer(Player player, Config config) {
        return config.isAutoReplantEnabled() && player.hasPermission("autotreechop.replant");
    }
}
