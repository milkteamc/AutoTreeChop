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
 
package org.milkteamc.autotreechop.utils;

import java.util.HashMap;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.Config;

public class CooldownManager {

    private final HashMap<UUID, Long> cooldowns = new HashMap<>();

    public CooldownManager() {}

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
