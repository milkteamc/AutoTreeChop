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

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class GriefPreventionHook {
    private final String flagName;

    public GriefPreventionHook(String flagName) {
        this.flagName = flagName;
    }

    public boolean checkBuild(Player player, Location location) {
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);

        if (claim == null) {
            return true;
        }

        if (claim.getOwnerID().equals(player.getUniqueId())
                || player.hasPermission("autotreechop.op")
                || player.isOp()) {
            return true;
        }

        return claim.hasExplicitPermission(player, ClaimPermission.valueOf(flagName));
    }
}
