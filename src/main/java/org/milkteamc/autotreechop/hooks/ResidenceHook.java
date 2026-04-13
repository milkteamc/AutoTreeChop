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

import com.bekvon.bukkit.residence.api.ResidenceApi;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class ResidenceHook {
    private final String flagName;

    public ResidenceHook(String flagName) {
        this.flagName = flagName;
    }

    public boolean checkBuild(Player player, Location location) {
        ClaimedResidence residence = ResidenceApi.getResidenceManager().getByLoc(location);

        if (residence == null) {
            return true;
        }

        if (residence.getOwnerUUID().equals(player.getUniqueId())
                || player.isOp()
                || player.hasPermission("autotreechop.op")) {
            return true;
        }

        return residence.getPermissions().playerHas(player, Flags.valueOf(flagName.toLowerCase()), true);
    }
}
