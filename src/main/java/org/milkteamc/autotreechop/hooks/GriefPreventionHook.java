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
