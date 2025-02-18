package org.milkteamc.autotreechop.utils;

import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.PlayerConfig;

public class PermissionUtils {

    // VIP limit checker
    public static boolean hasVipUses(Player player, PlayerConfig playerConfig, Config config) {
        if (!config.getLimitVipUsage()) return player.hasPermission("autotreechop.vip");
        if (player.hasPermission("autotreechop.vip")) return playerConfig.getDailyUses() <= config.getVipUsesPerDay();
        return false;
    }

    public static boolean hasVipBlock(Player player, PlayerConfig playerConfig, Config config) {
        if (!config.getLimitVipUsage()) return player.hasPermission("autotreechop.vip");
        if (player.hasPermission("autotreechop.vip"))
            return playerConfig.getDailyBlocksBroken() <= config.getVipBlocksPerDay();
        return false;
    }
}