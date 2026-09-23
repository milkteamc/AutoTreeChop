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

import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.PlayerConfig;

public final class ActivationUtils {
    private ActivationUtils() {}

    /** Activation only; callers must still check permissions, quotas, cooldowns and protection. */
    public static boolean isActive(Player player, PlayerConfig data, Config config) {
        return switch (PreferenceUtils.activation(data.getPreferences(), config)) {
            case DISABLED -> false;
            case SNEAK -> player.isSneaking();
            case COMMAND_AND_SNEAK -> data.isAutoTreeChopEnabled() && player.isSneaking();
            default -> data.isAutoTreeChopEnabled();
        };
    }
}
