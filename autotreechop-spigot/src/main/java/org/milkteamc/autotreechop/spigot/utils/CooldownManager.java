package org.milkteamc.autotreechop.spigot.utils;

import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.spigot.AutoTreeChop;
import org.milkteamc.autotreechop.spigot.Config;

import java.util.HashMap;
import java.util.UUID;

public class CooldownManager {

    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final AutoTreeChop plugin;

    public CooldownManager(AutoTreeChop plugin) {
        this.plugin = plugin;
    }


    public void setCooldown(Player player, UUID playerUUID, Config config) {
        if (player.hasPermission("autotreechop.vip")) {
            cooldowns.put(playerUUID, System.currentTimeMillis() + (config.getVipCooldownTime() * 1000L));
        } else {
            cooldowns.put(playerUUID, System.currentTimeMillis() + (config.getCooldownTime() * 1000L));
        }
    }

    public boolean isInCooldown(UUID playerUUID) {
        Long cooldownEnd = cooldowns.get(playerUUID);
        if (cooldownEnd == null) {
            return false;
        }
        return System.currentTimeMillis() < cooldownEnd;
    }

    public long getRemainingCooldown(UUID playerUUID) {
        Long cooldownEnd = cooldowns.get(playerUUID);
        if (cooldownEnd == null) {
            return 0;
        }
        long remainingTime = cooldownEnd - System.currentTimeMillis();
        return Math.max(0, remainingTime / 1000);
    }
}