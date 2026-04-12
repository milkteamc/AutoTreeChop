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
 
package org.milkteamc.autotreechop.events;

import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.PlayerConfig;
import org.milkteamc.autotreechop.utils.SessionManager;

public class PlayerQuitListener implements Listener {

    private final AutoTreeChop plugin;

    public PlayerQuitListener(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        PlayerConfig playerConfig = plugin.getDataManager().getPlayerConfig(playerUUID);

        if (playerConfig != null && playerConfig.isDirty()) {
            plugin.getDatabaseManager().savePlayerDataSync(playerConfig.getData());
        }

        plugin.getDataManager().removePlayerConfig(playerUUID);
        SessionManager.getInstance().clearAllPlayerSessions(playerUUID);
        SessionManager.getInstance().finishLeafCheck(playerUUID);
        plugin.getConfirmationManager().clearPlayer(playerUUID);
    }
}
