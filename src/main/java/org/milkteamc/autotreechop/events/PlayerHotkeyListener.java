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

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.MessageKeys;
import org.milkteamc.autotreechop.configuration.ActivationMode;
import org.milkteamc.autotreechop.utils.PreferenceUtils;

public final class PlayerHotkeyListener implements Listener {
    private final AutoTreeChop plugin;

    public PlayerHotkeyListener(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (event.isCancelled()) return;
        var player = event.getPlayer();
        if (!player.isSneaking() || !player.hasPermission("autotreechop.use")) return;
        var data = plugin.getDataManager().getPlayerConfig(player.getUniqueId());
        if (data == null
                || PreferenceUtils.activation(data.getPreferences(), plugin.getPluginConfig()) != ActivationMode.HOTKEY)
            return;
        event.setCancelled(true);
        boolean enabled = !data.isAutoTreeChopEnabled();
        data.setAutoTreeChopEnabled(enabled);
        if (!enabled) plugin.getConfirmationManager().clearPlayer(player.getUniqueId());
        AutoTreeChop.sendMessage(player, enabled ? MessageKeys.ENABLED : MessageKeys.DISABLED);
    }
}
