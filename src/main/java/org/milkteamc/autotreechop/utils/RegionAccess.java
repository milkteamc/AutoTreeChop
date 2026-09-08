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

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.AutoTreeChop;

/** Check ownership before accessing live world/entity state on Folia. */
public final class RegionAccess {
    private RegionAccess() {}

    public static boolean owns(Player player) {
        return !AutoTreeChop.isFolia() || Bukkit.isOwnedByCurrentRegion(player);
    }

    public static boolean owns(Location location) {
        return !AutoTreeChop.isFolia() || Bukkit.isOwnedByCurrentRegion(location);
    }

    public static boolean ownsArea(Location center, int radius) {
        if (!AutoTreeChop.isFolia()) return true;
        for (int x = (center.getBlockX() - radius) >> 4; x <= (center.getBlockX() + radius) >> 4; x++) {
            for (int z = (center.getBlockZ() - radius) >> 4; z <= (center.getBlockZ() + radius) >> 4; z++) {
                if (!Bukkit.isOwnedByCurrentRegion(center.getWorld(), x, z)) return false;
            }
        }
        return true;
    }
}
