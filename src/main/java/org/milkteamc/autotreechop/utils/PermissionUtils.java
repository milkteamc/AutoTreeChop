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

    public static boolean hasUsePermission(Player player, Config config) {
        return config.isLiteMode() || player.hasPermission("autotreechop.use");
    }

    /** Whether the player has room for the proposed operation under their current group policy. */
    public static boolean canUse(Player player, PlayerConfig playerConfig, Config config) {
        return config.resolvePolicy(player).canUse(playerConfig.getDailyUses());
    }

    public static boolean canBreakBlocks(Player player, PlayerConfig playerConfig, Config config, int count) {
        return config.resolvePolicy(player).canBreakBlocks(playerConfig.getDailyBlocksBroken(), count);
    }

    /** @deprecated Retains legacy VIP semantics; use {@link #canUse} for group quotas. */
    @Deprecated(since = "1.8.0", forRemoval = false)
    public static boolean hasVipUses(Player player, PlayerConfig playerConfig, Config config) {
        return player.hasPermission("autotreechop.vip") && !config.getLimitVipUsage();
    }

    /** @deprecated Retains legacy VIP semantics; use {@link #canBreakBlocks} for group quotas. */
    @Deprecated(since = "1.8.0", forRemoval = false)
    public static boolean hasVipBlock(Player player, PlayerConfig playerConfig, Config config) {
        return hasVipUses(player, playerConfig, config);
    }
}
