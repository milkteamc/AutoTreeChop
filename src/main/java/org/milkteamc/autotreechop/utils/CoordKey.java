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

import java.util.Objects;
import org.bukkit.Location;

/**
 * Immutable block-coordinate key that provides correct O(1) hash-based equality
 * for block positions.
 *
 * <p>Bukkit's {@link Location#equals} and {@link Location#hashCode} include yaw and
 * pitch as floating-point fields.  Block locations obtained from
 * {@code block.getLocation()} always carry {@code yaw=0f / pitch=0f}, so a plain
 * {@code Set<Location>} works for those – but any code that formerly used
 * {@code stream().anyMatch()} with manual coordinate comparison was already
 * bypassing the hash entirely, paying O(n) per lookup.  This class guarantees
 * O(1) regardless of how the Location was constructed.
 */
public final class CoordKey {

    private final int x;
    private final int y;
    private final int z;
    private final String world;

    private CoordKey(int x, int y, int z, String world) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.world = world;
    }

    /** Build a key from a {@link Location} (uses block coordinates). */
    public static CoordKey of(Location loc) {
        return new CoordKey(
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                loc.getWorld() != null ? loc.getWorld().getName() : "");
    }

    /** Build a key from raw integer block coordinates and a world name. */
    public static CoordKey of(int x, int y, int z, String worldName) {
        return new CoordKey(x, y, z, worldName != null ? worldName : "");
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getWorld() {
        return world;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CoordKey other)) return false;
        return x == other.x && y == other.y && z == other.z && Objects.equals(world, other.world);
    }

    @Override
    public int hashCode() {
        int h = x;
        h = h * 31 + y;
        h = h * 31 + z;
        h = h * 31 + (world != null ? world.hashCode() : 0);
        return h;
    }

    @Override
    public String toString() {
        return world + ":" + x + "," + y + "," + z;
    }
}
