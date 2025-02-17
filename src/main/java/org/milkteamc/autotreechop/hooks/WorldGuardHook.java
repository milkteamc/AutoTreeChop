package org.milkteamc.autotreechop.hooks;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class WorldGuardHook {
    public boolean checkBuild(Player player, Location location) {
        if (player.hasPermission("autotreechop.op") || player.isOp()) {
            return true;
        }

        com.sk89q.worldedit.util.Location loc = BukkitAdapter.adapt(location);
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(loc);
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        return !(set.queryState(localPlayer, Flags.BUILD) == StateFlag.State.DENY) &&
                !(set.queryState(localPlayer, Flags.BLOCK_BREAK) == StateFlag.State.DENY);
    }
} 