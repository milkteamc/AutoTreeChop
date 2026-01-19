package org.milkteamc.autotreechop.utils;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of block data captured synchronously for async processing
 */
public class BlockSnapshot {
    private final Map<LocationKey, Material> blockData;
    private final World world;
    private final Location centerLocation;
    private static final Material AIR_MATERIAL = XMaterial.AIR.parseMaterial() != null ? 
            XMaterial.AIR.parseMaterial() : Material.AIR;

    public BlockSnapshot(Map<LocationKey, Material> blockData, World world, Location centerLocation) {
        this.blockData = new HashMap<>(blockData);
        this.world = world;
        this.centerLocation = centerLocation.clone();
    }

    public Material getBlockType(Location loc) {
        return blockData.getOrDefault(new LocationKey(loc), AIR_MATERIAL);
    }

    public Material getBlockType(int x, int y, int z) {
        return blockData.getOrDefault(new LocationKey(x, y, z), AIR_MATERIAL);
    }

    public boolean hasBlock(Location loc) {
        return blockData.containsKey(new LocationKey(loc));
    }

    public Set<LocationKey> getAllLocations() {
        return blockData.keySet();
    }

    public World getWorld() {
        return world;
    }

    public Location getCenterLocation() {
        return centerLocation.clone();
    }

    /**
     * Lightweight location key for HashMap
     */
    public static class LocationKey {
        private final int x;
        private final int y;
        private final int z;
        private final int hash;

        public LocationKey(Location loc) {
            this.x = loc.getBlockX();
            this.y = loc.getBlockY();
            this.z = loc.getBlockZ();
            this.hash = computeHash();
        }

        public LocationKey(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.hash = computeHash();
        }

        private int computeHash() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }

        public Location toLocation(World world) {
            return new Location(world, x, y, z);
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LocationKey)) return false;
            LocationKey that = (LocationKey) o;
            return x == that.x && y == that.y && z == that.z;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + "," + z + ")";
        }
    }
}