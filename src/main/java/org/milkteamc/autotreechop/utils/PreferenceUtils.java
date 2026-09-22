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
import org.milkteamc.autotreechop.PlayerPreferences;
import org.milkteamc.autotreechop.configuration.ActivationMode;

public final class PreferenceUtils {
    private PreferenceUtils() {}

    public static ActivationMode activation(PlayerPreferences preferences, Config config) {
        ActivationMode server = config.getActivationMode();
        if (server == ActivationMode.DISABLED || preferences.activation() == PlayerPreferences.Activation.DEFAULT)
            return server;
        return ActivationMode.valueOf(preferences.activation().name());
    }

    public static boolean commandEnabled(PlayerConfig data, Config config) {
        ActivationMode mode = activation(data.getPreferences(), config);
        return mode == ActivationMode.COMMAND || mode == ActivationMode.COMMAND_AND_SNEAK;
    }

    public static boolean sneakMessages(PlayerPreferences preferences, Config config) {
        return preferences.sneakMessages().resolve(config.getSneakMessage());
    }

    public static boolean leafRemoval(Player player, PlayerPreferences preferences, Config config) {
        return config.isLeafRemovalEnabled()
                && preferences.leafRemoval().resolve(true)
                && player.hasPermission("autotreechop.leaves");
    }

    public static boolean autoReplant(Player player, PlayerPreferences preferences, Config config) {
        return config.isAutoReplantEnabled()
                && preferences.autoReplant().resolve(true)
                && player.hasPermission("autotreechop.replant");
    }
}
