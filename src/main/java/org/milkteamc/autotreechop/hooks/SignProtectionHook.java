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
 
package org.milkteamc.autotreechop.hooks;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.plugin.Plugin;
import org.milkteamc.autotreechop.utils.RegionAccess;

/** Prevents a chop from removing the support of a BlockLocker or LockettePro sign. */
public final class SignProtectionHook {
    private static final BlockFace[] FACES = {
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    public enum Result {
        SAFE,
        PROTECTED,
        INCOMPLETE
    }

    private final Predicate<Block> protectedBlock;
    private final Predicate<Block> protectedSign;

    public SignProtectionHook(Plugin blockLocker, Plugin lockettePro, Logger logger) {
        Predicate<Block> blockLockerBlock =
                apiCheck(blockLocker, "nl.rutgerkok.blocklocker.BlockLockerAPIv2", "isProtected", logger, false);
        Predicate<Block> locketteBlock =
                apiCheck(lockettePro, "me.crafter.mc.lockettepro.LocketteProAPI", "isProtected", logger, false);
        Predicate<Block> blockLockerSign =
                apiCheck(blockLocker, "nl.rutgerkok.blocklocker.BlockLockerAPIv2", "isProtected", logger, true);
        Predicate<Block> locketteSign = apiCheck(
                lockettePro, "me.crafter.mc.lockettepro.LocketteProAPI", "isLockSignOrAdditionalSign", logger, true);
        this.protectedBlock = blockLockerBlock.or(locketteBlock);
        this.protectedSign = blockLockerSign.or(locketteSign);
    }

    SignProtectionHook(Predicate<Block> protectedBlock, Predicate<Block> protectedSign) {
        this.protectedBlock = protectedBlock;
        this.protectedSign = protectedSign;
    }

    public Result inspect(Set<Location> logs) {
        for (Location location : logs) {
            Result result = inspect(location);
            if (result != Result.SAFE) return result;
        }
        return Result.SAFE;
    }

    public Result inspect(Location location) {
        if (!readable(location) || !RegionAccess.ownsArea(location, 2)) return Result.INCOMPLETE;
        Block log = location.getBlock();
        if (protectedBlock.test(log)) return Result.PROTECTED;
        for (BlockFace face : FACES) {
            Location adjacent = location.clone().add(face.getModX(), face.getModY(), face.getModZ());
            if (adjacent.getBlockY() < adjacent.getWorld().getMinHeight()
                    || adjacent.getBlockY() >= adjacent.getWorld().getMaxHeight()) continue;
            if (!readable(adjacent)) return Result.INCOMPLETE;
            Block neighbor = adjacent.getBlock();
            if (supportedSign(neighbor, face) && protectedSign.test(neighbor)) return Result.PROTECTED;
        }
        return Result.SAFE;
    }

    static boolean supportedSign(Block neighbor, BlockFace face) {
        Material type = neighbor.getType();
        String name = type.name();
        if (!name.endsWith("_SIGN")) return false;
        if (name.endsWith("_WALL_SIGN") || name.endsWith("_WALL_HANGING_SIGN")) {
            return neighbor.getBlockData() instanceof Directional directional && directional.getFacing() == face;
        }
        if (name.endsWith("_HANGING_SIGN")) return face == BlockFace.DOWN;
        return face == BlockFace.UP;
    }

    private static boolean readable(Location location) {
        return RegionAccess.owns(location)
                && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private static Predicate<Block> apiCheck(
            Plugin plugin, String className, String methodName, Logger logger, boolean failClosed) {
        if (plugin == null || !plugin.isEnabled()) return block -> false;
        try {
            Class<?> api = Class.forName(className, false, plugin.getClass().getClassLoader());
            Method method = api.getMethod(methodName, Block.class);
            AtomicBoolean warned = new AtomicBoolean();
            return block -> {
                if (!plugin.isEnabled()) return false;
                try {
                    return Boolean.TRUE.equals(method.invoke(null, block));
                } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
                    if (warned.compareAndSet(false, true))
                        logger.warning("Could not query " + plugin.getName() + " protection: " + error);
                    return failClosed;
                }
            };
        } catch (ReflectiveOperationException | LinkageError error) {
            logger.warning("Could not load " + plugin.getName() + " protection API: " + error);
            return block -> failClosed && plugin.isEnabled();
        }
    }
}
