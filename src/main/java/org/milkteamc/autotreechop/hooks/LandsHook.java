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
