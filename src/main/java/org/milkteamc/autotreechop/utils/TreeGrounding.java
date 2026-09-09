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

import java.util.HashSet;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.milkteamc.autotreechop.Config;

/** Ground contact and original planting sites captured before any tree blocks are removed. */
public final class TreeGrounding {
    private TreeGrounding() {}

    public record Result(boolean grounded, Set<Location> plantableBases) {}

    /** Returns null when region ownership prevents a complete support check. */
    public static Result inspect(Set<Location> treeBlocks, Config config) {
        boolean grounded = false;
        Set<Location> bases = new HashSet<>();
        for (Location location : treeBlocks) {
            Location below = location.clone().subtract(0, 1, 0);
            if (treeBlocks.contains(below)) continue;
            if (!RegionAccess.owns(location) || !RegionAccess.owns(below)) return null;
            if (below.getBlockY() < below.getWorld().getMinHeight()) continue;
            if (!BlockDiscoveryUtils.isLog(location.getBlock().getType(), config)) continue;
            Material support = below.getBlock().getType();
            if (BlockDiscoveryUtils.isLog(support, config) || BlockDiscoveryUtils.isLeafBlock(support, config))
                continue;
            if (support.isSolid()) {
                grounded = true;
                if (TreeReplantUtils.isValidSoil(support, config)) bases.add(location.clone());
            }
        }
        return new Result(grounded, Set.copyOf(bases));
    }
}
