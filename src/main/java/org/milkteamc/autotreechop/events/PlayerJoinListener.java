package org.milkteamc.autotreechop.events;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.PlayerConfig;
import org.milkteamc.autotreechop.database.DatabaseManager;

import java.util.UUID;

public class PlayerJoinListener implements Listener {

    private final AutoTreeChop plugin;

    public PlayerJoinListener(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        plugin.getDatabaseManager().loadPlayerDataAsync(playerUUID, plugin.getPluginConfig().getDefaultTreeChop())
                .thenAccept(data -> {
                    PlayerConfig playerConfig = new PlayerConfig(playerUUID, data);
                    plugin.getAllPlayerConfigs().put(playerUUID, playerConfig);
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to load data for player " + player.getName() + ": " + ex.getMessage());
                    DatabaseManager.PlayerData defaultData = new DatabaseManager.PlayerData(
                            playerUUID,
                            plugin.getPluginConfig().getDefaultTreeChop(),
                            0,
                            0,
                            java.time.LocalDate.now()
                    );
                    plugin.getAllPlayerConfigs().put(playerUUID, new PlayerConfig(playerUUID, defaultData));
                    return null;
                });
    }
}