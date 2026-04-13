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

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.hooks.GriefPreventionHook;
import org.milkteamc.autotreechop.hooks.LandsHook;
import org.milkteamc.autotreechop.hooks.ResidenceHook;
import org.milkteamc.autotreechop.hooks.WorldGuardHook;

public class ProtectionCheckUtils {

    /**
     * Unified permission check.
     *
     * @param player   Player to check
     * @param location Location to check
     * @param hooks    Protection hooks container
     * @return true if player can build at location, false otherwise
     */
    public static boolean canModifyBlock(Player player, Location location, ProtectionHooks hooks) {

        if (hooks.worldGuardEnabled) {
            if (!hooks.worldGuard.checkBuild(player, location)) {
                return false;
            }
        }

        if (hooks.residenceEnabled) {
            if (!hooks.residence.checkBuild(player, location)) {
                return false;
            }
        }

        if (hooks.griefPreventionEnabled) {
            if (!hooks.griefPrevention.checkBuild(player, location)) {
                return false;
            }
        }

        if (hooks.landsEnabled) {
            if (!hooks.lands.checkBuild(player, location)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Batch permission check for multiple locations.
     * Uses early-exit optimization - stops checking once first failure is found.
     *
     * @param player    Player to check
     * @param locations Locations to check
     * @param hooks     Protection hooks container
     * @return true if player can build at ALL locations, false otherwise
     */
    public static boolean canModifyAllBlocks(Player player, Iterable<Location> locations, ProtectionHooks hooks) {
        for (Location location : locations) {
            if (!canModifyBlock(player, location, hooks)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Individual check methods (for backwards compatibility or specific use cases)
     */
    public static boolean checkWorldGuard(Player player, Location location, boolean enabled, WorldGuardHook hook) {
        return !enabled || hook.checkBuild(player, location);
    }

    public static boolean checkResidence(Player player, Location location, boolean enabled, ResidenceHook hook) {
        return !enabled || hook.checkBuild(player, location);
    }

    public static boolean checkGriefPrevention(
            Player player, Location location, boolean enabled, GriefPreventionHook hook) {
        return !enabled || hook.checkBuild(player, location);
    }

    public static boolean checkLands(Player player, Location location, boolean enabled, LandsHook hook) {
        return !enabled || hook.checkBuild(player, location);
    }

    public static class ProtectionHooks {
        public final boolean worldGuardEnabled;
        public final boolean residenceEnabled;
        public final boolean griefPreventionEnabled;
        public final boolean landsEnabled;

        public final WorldGuardHook worldGuard;
        public final ResidenceHook residence;
        public final GriefPreventionHook griefPrevention;
        public final LandsHook lands;

        public ProtectionHooks(
                boolean worldGuardEnabled,
                WorldGuardHook worldGuard,
                boolean residenceEnabled,
                ResidenceHook residence,
                boolean griefPreventionEnabled,
                GriefPreventionHook griefPrevention,
                boolean landsEnabled,
                LandsHook lands) {
            this.worldGuardEnabled = worldGuardEnabled;
            this.worldGuard = worldGuard;
            this.residenceEnabled = residenceEnabled;
            this.residence = residence;
            this.griefPreventionEnabled = griefPreventionEnabled;
            this.griefPrevention = griefPrevention;
            this.landsEnabled = landsEnabled;
            this.lands = lands;
        }
    }
}
