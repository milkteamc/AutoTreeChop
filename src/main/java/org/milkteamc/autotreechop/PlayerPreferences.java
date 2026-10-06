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
 
package org.milkteamc.autotreechop;

import java.util.Locale;
import java.util.Objects;

/** Immutable saved overrides. DEFAULT follows the current server setting. */
public record PlayerPreferences(Activation activation, Toggle sneakMessages, Toggle leafRemoval, Toggle autoReplant) {
    public static final PlayerPreferences DEFAULTS =
            new PlayerPreferences(Activation.DEFAULT, Toggle.DEFAULT, Toggle.DEFAULT, Toggle.DEFAULT);

    public PlayerPreferences {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(sneakMessages, "sneakMessages");
        Objects.requireNonNull(leafRemoval, "leafRemoval");
        Objects.requireNonNull(autoReplant, "autoReplant");
    }

    public enum Activation {
        DEFAULT,
        DISABLED,
        COMMAND,
        SNEAK,
        COMMAND_AND_SNEAK,
        HOTKEY;

        public String value() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        public static Activation parse(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        }
    }

    public enum Toggle {
        DEFAULT,
        ON,
        OFF;

        public boolean resolve(boolean fallback) {
            return this == DEFAULT ? fallback : this == ON;
        }

        public String value() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Toggle parse(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    public PlayerPreferences withActivation(Activation value) {
        return new PlayerPreferences(value, sneakMessages, leafRemoval, autoReplant);
    }

    public PlayerPreferences withSneakMessages(Toggle value) {
        return new PlayerPreferences(activation, value, leafRemoval, autoReplant);
    }

    public PlayerPreferences withLeafRemoval(Toggle value) {
        return new PlayerPreferences(activation, sneakMessages, value, autoReplant);
    }

    public PlayerPreferences withAutoReplant(Toggle value) {
        return new PlayerPreferences(activation, sneakMessages, leafRemoval, value);
    }
}
