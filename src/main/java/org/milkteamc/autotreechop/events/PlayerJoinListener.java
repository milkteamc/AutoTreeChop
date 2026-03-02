package org.milkteamc.autotreechop.events;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.PlayerConfig;
import org.milkteamc.autotreechop.database.DatabaseManager;

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
                    plugin.getAllPlayerConfigs().put(playerUUID, playerConfig);

                    // markRejoin must be called here, after playerConfig is loaded,
                    // so we know whether ATC was enabled for this player.
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
                    plugin.getAllPlayerConfigs().put(playerUUID, fallback);
                    // Default is disabled, so no markRejoin needed here.
                    return null;
                });
    }
}
