package org.milkteamc.autotreechop;

import org.bukkit.entity.Player;

import java.util.UUID;

public class AutoTreeChopAPI {

    private final AutoTreeChop plugin;

    public AutoTreeChopAPI(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    /**
     * Get if AutoTreeChop is enabled
     *
     * @param player
     * @return boolean
     */
    public boolean isAutoTreeChopEnabled(Player player) {
        PlayerConfig playerConfig = plugin.getPlayerConfig(player.getUniqueId());
        return playerConfig.isAutoTreeChopEnabled();
    }

    /**
     * Set specific player AutoTreeChop as enabled
     *
     * @param player
     */
    public void enableAutoTreeChop(Player player) {
        PlayerConfig playerConfig = plugin.getPlayerConfig(player.getUniqueId());
        playerConfig.setAutoTreeChopEnabled(true);
    }

    /**
     * Set specific player AutoTreeChop as disable
     *
     * @param player
     */
    public void disableAutoTreeChop(Player player) {
        PlayerConfig playerConfig = plugin.getPlayerConfig(player.getUniqueId());
        playerConfig.setAutoTreeChopEnabled(false);
    }

    /**
     * Get how many times player use AutoTreeChop today
     *
     * @param playerUUID
     * @return int
     */
    public int getPlayerDailyUses(UUID playerUUID) {
        return plugin.getPlayerDailyUses(playerUUID);
    }

    /**
     * Get how many blocks player break via AutoTreeChop today
     *
     * @param playerUUID
     * @return int
     */
    public int getPlayerDailyBlocksBroken(UUID playerUUID) {
        return plugin.getPlayerDailyBlocksBroken(playerUUID);
    }
}
