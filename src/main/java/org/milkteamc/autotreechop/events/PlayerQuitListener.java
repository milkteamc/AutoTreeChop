package org.milkteamc.autotreechop.events;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.PlayerConfig;
import org.milkteamc.autotreechop.utils.SessionManager;

import java.util.UUID;

public class PlayerQuitListener implements Listener {

    private final AutoTreeChop plugin;

    public PlayerQuitListener(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        PlayerConfig playerConfig = plugin.getAllPlayerConfigs().get(playerUUID);
        if (playerConfig != null && playerConfig.isDirty()) {
            plugin.getDatabaseManager().savePlayerDataSync(playerConfig.getData());
        }

        plugin.getAllPlayerConfigs().remove(playerUUID);

        SessionManager.getInstance().clearAllPlayerSessions(playerUUID);
    }
}