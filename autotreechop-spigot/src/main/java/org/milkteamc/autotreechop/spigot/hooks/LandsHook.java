package org.milkteamc.autotreechop.spigot.hooks;

import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.land.LandWorld;
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
        if (world == null) {
            return true; // Lands is not enabled in this world
        }

        if (player.hasPermission("autotreechop.op") || player.isOp()) {
            return true;
        }

        return world.hasFlag(player, location, null, me.angeschossen.lands.api.flags.Flags.BLOCK_BREAK, false);
    }
} 