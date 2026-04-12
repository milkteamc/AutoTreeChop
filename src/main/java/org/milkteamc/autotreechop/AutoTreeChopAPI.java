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
import org.bukkit.entity.Player;

public class AutoTreeChopAPI {

    private final AutoTreeChop plugin;

    public AutoTreeChopAPI(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    /**
     * Get if AutoTreeChop is enabled
     *
     * @return boolean
     */
    public boolean isAutoTreeChopEnabled(Player player) {
        PlayerConfig playerConfig = plugin.getDataManager().getPlayerConfig(player.getUniqueId());
        return playerConfig.isAutoTreeChopEnabled();
    }

    /**
     * Set specific player AutoTreeChop as enabled
     */
    public void enableAutoTreeChop(Player player) {
        PlayerConfig playerConfig = plugin.getDataManager().getPlayerConfig(player.getUniqueId());
        playerConfig.setAutoTreeChopEnabled(true);
    }

    /**
     * Set specific player AutoTreeChop as disable
     */
    public void disableAutoTreeChop(Player player) {
        PlayerConfig playerConfig = plugin.getDataManager().getPlayerConfig(player.getUniqueId());
        playerConfig.setAutoTreeChopEnabled(false);
    }

    /**
     * Get how many times player use AutoTreeChop today
     *
     * @return int
     */
    public int getPlayerDailyUses(UUID playerUUID) {
        return plugin.getDataManager().getPlayerDailyUses(playerUUID);
    }

    /**
     * Get how many blocks player break via AutoTreeChop today
     *
     * @return int
     */
    public int getPlayerDailyBlocksBroken(UUID playerUUID) {
        return plugin.getDataManager().getPlayerDailyBlocksBroken(playerUUID);
    }
}
