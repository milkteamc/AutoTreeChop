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
import org.bukkit.block.data.type.Leaves;
import org.milkteamc.autotreechop.Config;

/** Bounded, synchronous checks for construction evidence around the discovered logs. */
public final class TreeStructureDetector {
    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final Set<String> CONSTRUCTION_BLOCKS =
            Set.of("GLASS", "GLASS_PANE", "BRICKS", "CHEST", "TRAPPED_CHEST", "BARREL", "CRAFTING_TABLE", "FURNACE");

    public enum Result {
        NO_EVIDENCE,
        SUSPICIOUS,
        INCOMPLETE
    }

    private TreeStructureDetector() {}

    public static Result inspect(Set<Location> logs, Config config) {
        boolean construction = false;
        boolean decorativeLeaves = false;
        boolean naturalLeaves = false;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        Set<Location> checkedNeighbors = new HashSet<>();
        for (Location location : logs) {
            if (!readable(location)) return Result.INCOMPLETE;
            Material type = location.getBlock().getType();
            if (!BlockDiscoveryUtils.isLog(type, config)) continue;
            String name = type.name();
            construction |= name.startsWith("STRIPPED_") || name.endsWith("_WOOD") || name.endsWith("_HYPHAE");
            minX = Math.min(minX, location.getBlockX());
            minY = Math.min(minY, location.getBlockY());
            minZ = Math.min(minZ, location.getBlockZ());
            maxX = Math.max(maxX, location.getBlockX());
            maxY = Math.max(maxY, location.getBlockY());
            maxZ = Math.max(maxZ, location.getBlockZ());
            for (int[] offset : FACES) {
                Location neighbor = location.clone().add(offset[0], offset[1], offset[2]);
                if (!checkedNeighbors.add(neighbor)) continue;
                if (neighbor.getBlockY() < neighbor.getWorld().getMinHeight()
                        || neighbor.getBlockY() >= neighbor.getWorld().getMaxHeight()) continue;
                if (!readable(neighbor)) return Result.INCOMPLETE;
                var block = neighbor.getBlock();
                construction |= isConstruction(block.getType());
                if (BlockDiscoveryUtils.isLeafBlock(block.getType(), config)) {
                    if (block.getBlockData() instanceof Leaves leaves && leaves.isPersistent()) decorativeLeaves = true;
                    else naturalLeaves = true;
                }
            }
        }
        boolean flatStructure = minY != Integer.MAX_VALUE
                && (long) maxY - minY <= 1
                && Math.max((long) maxX - minX, (long) maxZ - minZ) >= 4;
        return construction || flatStructure || (decorativeLeaves && !naturalLeaves)
                ? Result.SUSPICIOUS
                : Result.NO_EVIDENCE;
    }

    private static boolean readable(Location location) {
        return RegionAccess.owns(location)
                && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private static boolean isConstruction(Material material) {
        String name = material.name();
        return name.endsWith("_PLANKS")
                || name.endsWith("_STAIRS")
                || name.endsWith("_SLAB")
                || name.endsWith("_FENCE")
                || name.endsWith("_FENCE_GATE")
                || name.endsWith("_DOOR")
                || name.endsWith("_TRAPDOOR")
                || name.endsWith("_WALL")
                || name.endsWith("_SIGN")
                || name.endsWith("_BRICKS")
                || name.endsWith("_GLASS")
                || name.endsWith("_GLASS_PANE")
                || CONSTRUCTION_BLOCKS.contains(name);
    }
}
