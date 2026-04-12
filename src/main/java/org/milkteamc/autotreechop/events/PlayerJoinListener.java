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

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.PlayerConfig;
import org.milkteamc.autotreechop.database.DatabaseManager;
import org.milkteamc.autotreechop.updater.ModrinthUpdateChecker;

public class PlayerJoinListener implements Listener {

    private final AutoTreeChop plugin;

    public PlayerJoinListener(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        plugin.getDatabaseManager()
                .loadPlayerDataAsync(playerUUID, plugin.getPluginConfig().getDefaultTreeChop())
                .thenAccept(data -> {
                    PlayerConfig playerConfig = new PlayerConfig(playerUUID, data);
                    plugin.getDataManager().getAllPlayerConfigs().put(playerUUID, playerConfig);

                    if (playerConfig.isAutoTreeChopEnabled()) {
                        plugin.getConfirmationManager().markRejoin(playerUUID);
                    }
                })
                .exceptionally(ex -> {
                    plugin.getLogger()
                            .warning("Failed to load data for player " + player.getName() + ": " + ex.getMessage());
                    DatabaseManager.PlayerData defaultData = new DatabaseManager.PlayerData(
                            playerUUID, plugin.getPluginConfig().getDefaultTreeChop(), 0, 0, java.time.LocalDate.now());
                    PlayerConfig fallback = new PlayerConfig(playerUUID, defaultData);
                    plugin.getDataManager().getAllPlayerConfigs().put(playerUUID, fallback);
                    return null;
                });
        ModrinthUpdateChecker checker = plugin.getUpdateChecker();
        if (checker != null && checker.shouldNotifyPlayer(player)) {
            UniversalScheduler.getScheduler(plugin).runTaskLater(() -> checker.notifyPlayer(player), 40L); // 2s delay
        }
    }
}
