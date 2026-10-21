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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.configuration.GroupPolicies.Policy;

class CooldownManagerTest {
    private final Player player = mock(Player.class);
    private final Config config = mock(Config.class);
    private final AtomicLong now = new AtomicLong(1000);
    private final CooldownManager cooldowns = new CooldownManager(now::get);
    private final UUID uuid = UUID.randomUUID();

    @Test
    void usesGroupCooldownEvenForUnlimitedPlayersAndKeepsExistingExpiry() {
        when(config.resolvePolicy(player)).thenReturn(new Policy("builder", false, 0, 0, 9));
        cooldowns.setCooldown(player, uuid, config);
        assertTrue(cooldowns.isInCooldown(uuid));
        assertEquals(9, cooldowns.getRemainingCooldown(uuid));
        when(config.resolvePolicy(player)).thenReturn(new Policy("donor", true, 5, 50, 2));
        now.addAndGet(8000);
        assertEquals(1, cooldowns.getRemainingCooldown(uuid));
        now.addAndGet(1000);
        assertFalse(cooldowns.isInCooldown(uuid));
        cooldowns.setCooldown(player, uuid, config);
        assertEquals(2, cooldowns.getRemainingCooldown(uuid));
    }

    @Test
    void zeroDisablesCooldownAndLargeCooldownDoesNotOverflow() {
        when(config.resolvePolicy(player)).thenReturn(new Policy("builder", true, 1, 1, 0));
        cooldowns.setCooldown(player, uuid, config);
        assertFalse(cooldowns.isInCooldown(uuid));
        assertEquals(0, cooldowns.getRemainingCooldown(uuid));
        when(config.resolvePolicy(player)).thenReturn(new Policy("builder", true, 1, 1, Integer.MAX_VALUE));
        cooldowns.setCooldown(player, uuid, config);
        assertTrue(cooldowns.isInCooldown(uuid));
        assertEquals(Integer.MAX_VALUE, cooldowns.getRemainingCooldown(uuid));
    }
}
