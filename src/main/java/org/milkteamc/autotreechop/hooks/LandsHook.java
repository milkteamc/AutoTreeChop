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

import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.land.LandWorld;
import me.angeschossen.lands.api.player.LandPlayer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class LandsHook {
    private final LandsIntegration landsApi;

    public LandsHook(Plugin plugin) {
        this.landsApi = LandsIntegration.of(plugin);
    }

    public boolean checkBuild(Player player, Location location) {
        if (location.getWorld() == null) {
            return false;
        }

        LandWorld world = landsApi.getWorld(location.getWorld());
        LandPlayer landPlayer = landsApi.getLandPlayer(player.getUniqueId());
        if (world == null) {
            return true; // Lands is not enabled in this world
        }

        if (player.hasPermission("autotreechop.op") || player.isOp()) {
            return true;
        }

        return world.hasRoleFlag(
                landPlayer, location, me.angeschossen.lands.api.flags.type.Flags.BLOCK_BREAK, null, false);
    }
}
