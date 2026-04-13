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

import org.bukkit.Bukkit;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;

public class HookManager {

    private final AutoTreeChop plugin;
    private WorldGuardHook worldGuardHook = null;
    private ResidenceHook residenceHook = null;
    private GriefPreventionHook griefPreventionHook = null;
    private LandsHook landsHook = null;

    public HookManager(AutoTreeChop plugin, Config config) {
        this.plugin = plugin;
        initializeHooks(config);
    }

    private void initializeHooks(Config config) {
        if (Bukkit.getPluginManager().getPlugin("Residence") != null) {
            try {
                residenceHook = new ResidenceHook(config.getResidenceFlag());
                plugin.getLogger().info("Residence support enabled");
            } catch (Exception e) {
                plugin.getLogger()
                        .warning(
                                "Residence can't be hooked, please report this to our GitHub: https://github.com/milkteamc/AutoTreeChop/issues");
            }
        }

        if (Bukkit.getPluginManager().getPlugin("GriefPrevention") != null) {
            try {
                griefPreventionHook = new GriefPreventionHook(config.getGriefPreventionFlag());
                plugin.getLogger().info("GriefPrevention support enabled");
            } catch (Exception e) {
                plugin.getLogger()
                        .warning(
                                "GriefPrevention can't be hooked, please report this to our GitHub: https://github.com/milkteamc/AutoTreeChop/issues");
            }
        }

        if (Bukkit.getPluginManager().getPlugin("Lands") != null) {
            try {
                landsHook = new LandsHook(plugin);
                plugin.getLogger().info("Lands support enabled");
            } catch (Exception e) {
                plugin.getLogger()
                        .warning(
                                "Lands can't be hooked, please report this to our GitHub: https://github.com/milkteamc/AutoTreeChop/issues");
            }
        }

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                worldGuardHook = new WorldGuardHook();
                plugin.getLogger().info("WorldGuard support enabled");
            } catch (NoClassDefFoundError e) {
                plugin.getLogger()
                        .warning(
                                "WorldGuard can't be hooked, please report this to our GitHub: https://github.com/milkteamc/AutoTreeChop/issues");
            }
        }
    }

    public boolean isWorldGuardEnabled() {
        return worldGuardHook != null;
    }

    public boolean isResidenceEnabled() {
        return residenceHook != null;
    }

    public boolean isGriefPreventionEnabled() {
        return griefPreventionHook != null;
    }

    public boolean isLandsEnabled() {
        return landsHook != null;
    }

    public WorldGuardHook getWorldGuardHook() {
        return worldGuardHook;
    }

    public ResidenceHook getResidenceHook() {
        return residenceHook;
    }

    public GriefPreventionHook getGriefPreventionHook() {
        return griefPreventionHook;
    }

    public LandsHook getLandsHook() {
        return landsHook;
    }
}
