package org.milkteamc.autotreechop;

import org.bukkit.entity.Player;

import java.util.UUID;

public class AutoTreeChopAPI {

    private final AutoTreeChop plugin;

    public AutoTreeChopAPI(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    public boolean isAutoTreeChopEnabled(Player player) {
        PlayerConfig playerConfig = plugin.getPlayerConfig(player.getUniqueId());
        return playerConfig.isAutoTreeChopEnabled();
    }

    public void enableAutoTreeChop(Player player) {
        PlayerConfig playerConfig = plugin.getPlayerConfig(player.getUniqueId());
        playerConfig.setAutoTreeChopEnabled(true);
    }

    public void disableAutoTreeChop(Player player) {
        PlayerConfig playerConfig = plugin.getPlayerConfig(player.getUniqueId());
        playerConfig.setAutoTreeChopEnabled(false);
    }

    public int getPlayerDailyUses(UUID playerUUID) {
        return plugin.getPlayerDailyUses(playerUUID);
    }

    public int getPlayerDailyBlocksBroken(UUID playerUUID) {
        return plugin.getPlayerDailyBlocksBroken(playerUUID);
    }
}
