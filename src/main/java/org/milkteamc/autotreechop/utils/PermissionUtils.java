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

import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.PlayerConfig;

public class PermissionUtils {

    /** Whether the player has room for the proposed operation, including VIP limits. */
    public static boolean canUse(Player player, PlayerConfig playerConfig, Config config) {
        if (!config.getLimitUsage()) return true;
        boolean vip = player.hasPermission("autotreechop.vip");
        if (vip && !config.getLimitVipUsage()) return true;
        int limit = vip ? config.getVipUsesPerDay() : config.getMaxUsesPerDay();
        return playerConfig.getDailyUses() < limit;
    }

    public static boolean canBreakBlocks(Player player, PlayerConfig playerConfig, Config config, int count) {
        if (count < 0) throw new IllegalArgumentException("count must be nonnegative");
        if (!config.getLimitUsage()) return true;
        boolean vip = player.hasPermission("autotreechop.vip");
        if (vip && !config.getLimitVipUsage()) return true;
        int limit = vip ? config.getVipBlocksPerDay() : config.getMaxBlocksPerDay();
        return (long) playerConfig.getDailyBlocksBroken() + count <= limit;
    }

    /** Legacy helpers: true only for unlimited VIP usage. Use canUse/canBreakBlocks for quotas. */
    public static boolean hasVipUses(Player player, PlayerConfig playerConfig, Config config) {
        return player.hasPermission("autotreechop.vip") && !config.getLimitVipUsage();
    }

    public static boolean hasVipBlock(Player player, PlayerConfig playerConfig, Config config) {
        return hasVipUses(player, playerConfig, config);
    }
}
