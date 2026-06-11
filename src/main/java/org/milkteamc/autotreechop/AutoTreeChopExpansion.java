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
 
package org.milkteamc.autotreechop;

import java.util.UUID;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AutoTreeChopExpansion extends PlaceholderExpansion {

    private final AutoTreeChop plugin;

    public AutoTreeChopExpansion(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "autotreechop";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getPluginDescription().getAuthors().get(0);
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        UUID playerUUID = player.getUniqueId();

        if (params.equalsIgnoreCase("daily_uses")) {
            return String.valueOf(plugin.getDataManager().getPlayerDailyUses(playerUUID));
        } else if (params.equalsIgnoreCase("daily_blocks_broken")) {
            return String.valueOf(plugin.getDataManager().getPlayerDailyBlocksBroken(playerUUID));
        } else if (params.equalsIgnoreCase("status")) {
            return String.valueOf(
                    plugin.getDataManager().getPlayerConfig(playerUUID).isAutoTreeChopEnabled());
        }

        return null;
    }
}
