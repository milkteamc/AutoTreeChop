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

        if (residence.getOwnerUUID().equals(player.getUniqueId()) || 
            player.isOp() || 
            player.hasPermission("autotreechop.op")) {
            return true;
        }

        return residence.getPermissions().playerHas(player, Flags.valueOf(flagName.toLowerCase()), true);
    }
} 