package org.milkteamc.autotreechop.events;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.PlayerConfig;

import java.util.UUID;

import static org.milkteamc.autotreechop.AutoTreeChop.*;

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
                sendMessage(player, SNEAK_ENABLED_MESSAGE);
            }
        } else {
            playerConfig.setAutoTreeChopEnabled(false);
            if (plugin.getPluginConfig().getSneakMessage()) {
                sendMessage(player, SNEAK_DISABLED_MESSAGE);
            }
        }
    }
}