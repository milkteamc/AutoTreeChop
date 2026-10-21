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

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.Config;

public class CooldownManager {

    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public CooldownManager() {
        this(System::currentTimeMillis);
    }

    CooldownManager(LongSupplier clock) {
        this.clock = clock;
    }

    public void setCooldown(Player player, UUID playerUUID, Config config) {
        cooldowns.put(
                playerUUID, clock.getAsLong() + (config.resolvePolicy(player).cooldownSeconds() * 1000L));
    }

    public boolean isInCooldown(UUID playerUUID) {
        Long cooldownEnd = cooldowns.get(playerUUID);
        if (cooldownEnd == null) {
            return false;
        }
        return clock.getAsLong() < cooldownEnd;
    }

    public long getRemainingCooldown(UUID playerUUID) {
        Long cooldownEnd = cooldowns.get(playerUUID);
        if (cooldownEnd == null) {
            return 0;
        }
        long remainingTime = cooldownEnd - clock.getAsLong();
        return Math.max(0, remainingTime / 1000);
    }
}
