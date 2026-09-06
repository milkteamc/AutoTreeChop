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

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.PlayerConfig;

class PermissionUtilsTest {
    private final Player player = mock(Player.class);
    private final PlayerConfig data = mock(PlayerConfig.class);
    private final Config config = mock(Config.class);

    @BeforeEach
    void setup() {
        when(config.getLimitUsage()).thenReturn(true);
        when(config.getLimitVipUsage()).thenReturn(true);
        when(config.getMaxUsesPerDay()).thenReturn(50);
        when(config.getVipUsesPerDay()).thenReturn(100);
        when(config.getMaxBlocksPerDay()).thenReturn(100);
        when(config.getVipBlocksPerDay()).thenReturn(1000);
    }

    @Test
    void vipUsesStopExactlyAtLimit() {
        when(player.hasPermission("autotreechop.vip")).thenReturn(true);
        when(data.getDailyUses()).thenReturn(99);
        assertTrue(PermissionUtils.canUse(player, data, config));
        when(data.getDailyUses()).thenReturn(100);
        assertFalse(PermissionUtils.canUse(player, data, config));
    }

    @Test
    void wholeTreeMustFitVipBudget() {
        when(player.hasPermission("autotreechop.vip")).thenReturn(true);
        when(data.getDailyBlocksBroken()).thenReturn(990);
        assertTrue(PermissionUtils.canBreakBlocks(player, data, config, 10));
        assertFalse(PermissionUtils.canBreakBlocks(player, data, config, 11));
        when(data.getDailyBlocksBroken()).thenReturn(1000);
        assertFalse(PermissionUtils.canBreakBlocks(player, data, config, 1));
    }

    @Test
    void ordinaryPlayerUsesOrdinaryLimits() {
        when(data.getDailyUses()).thenReturn(50);
        when(data.getDailyBlocksBroken()).thenReturn(95);
        assertFalse(PermissionUtils.canUse(player, data, config));
        assertTrue(PermissionUtils.canBreakBlocks(player, data, config, 5));
        assertFalse(PermissionUtils.canBreakBlocks(player, data, config, 6));
    }

    @Test
    void disabledLimitsAlsoApplyToLeafChecks() {
        when(config.getLimitUsage()).thenReturn(false);
        when(data.getDailyUses()).thenReturn(Integer.MAX_VALUE);
        when(data.getDailyBlocksBroken()).thenReturn(Integer.MAX_VALUE);
        assertTrue(PermissionUtils.canUse(player, data, config));
        assertTrue(PermissionUtils.canBreakBlocks(player, data, config, 1));
    }

    @Test
    void unlimitedVipRemainsUnlimited() {
        when(player.hasPermission("autotreechop.vip")).thenReturn(true);
        when(config.getLimitVipUsage()).thenReturn(false);
        when(data.getDailyUses()).thenReturn(Integer.MAX_VALUE);
        when(data.getDailyBlocksBroken()).thenReturn(Integer.MAX_VALUE);
        assertTrue(PermissionUtils.canUse(player, data, config));
        assertTrue(PermissionUtils.canBreakBlocks(player, data, config, 100));
    }
}
