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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.MessageKeys;
import org.milkteamc.autotreechop.PlayerConfig;

public class PlayerSneakListener implements Listener {

    private final AutoTreeChop plugin;

    public PlayerSneakListener(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (!plugin.getPluginConfig().getSneakToggle()) return;

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (!player.hasPermission("autotreechop.use")) return;

        PlayerConfig playerConfig = plugin.getPlayerConfig(playerUUID);

        if (event.isSneaking()) {
            playerConfig.setAutoTreeChopEnabled(true);
            if (plugin.getPluginConfig().getSneakMessage()) {
                AutoTreeChop.sendMessage(player, MessageKeys.SNEAK_ENABLED);
            }
        } else {
            playerConfig.setAutoTreeChopEnabled(false);
            if (plugin.getPluginConfig().getSneakMessage()) {
                AutoTreeChop.sendMessage(player, MessageKeys.SNEAK_DISABLED);
            }
        }
    }
}
