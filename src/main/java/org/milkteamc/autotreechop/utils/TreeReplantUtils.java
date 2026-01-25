package org.milkteamc.autotreechop.utils;

import com.cryptomorin.xseries.XMaterial;
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
            WorldGuardHook worldGuardHook) {

        if (!config.isAutoReplantEnabled()) {
            return;
        }

        Material saplingType = config.getSaplingForLog(originalLogType);
        if (saplingType == null) {
            return;
        }

        Location originalLocation = brokenLogBlock.getLocation().clone();

        Runnable replantTask = () -> {
            Location plantLocation = findSuitablePlantLocation(originalLocation, config, plugin);

            if (plantLocation == null) {
                return;
            }

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

            if (planted) {
                if (config.getReplantVisualEffect()) {
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
