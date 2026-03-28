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
