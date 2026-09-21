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

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.database.DataManager;
import org.milkteamc.autotreechop.utils.RegionAccess;

/**
 * Public integration API, available through Bukkit's ServicesManager after AutoTreeChop enables.
 * UUID operations access only synchronized in-memory data and may be called from any thread.
 * No method loads offline data, blocks on SQL, sends messages, or grants chopping permissions.
 * See docs/API.md for setup, lifecycle, and compatibility examples.
 */
public class AutoTreeChopAPI {
    private final AutoTreeChop plugin;
    private volatile boolean active = true;

    /** Prefer the registered service or {@link AutoTreeChop#getAutoTreeChopAPI()}. */
    public AutoTreeChopAPI(AutoTreeChop plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** Result of setting a loaded player's preference; success is not an SQL durability guarantee. */
    public enum ChangeResult {
        /** The preference changed and is queued for the normal save cycle. */
        UPDATED,
        /** The preference already had the requested value. */
        UNCHANGED,
        /** Loading, failed loading, offline, or the plugin is unavailable. No change was made. */
        UNAVAILABLE
    }

    /**
     * Immutable saved preference and usage, not the current activation result.
     * Sneak-only mode uses posture; combined mode requires both this preference and sneaking.
     * Daily counters follow the server's local date and reset on access.
     */
    public record PlayerState(boolean enabled, int dailyUses, int dailyBlocksBroken) {}

    /**
     * Immutable effective group settings. When unlimited is true, both daily quotas are ignored;
     * cooldown still applies. cooldownSeconds is the configured duration, not the remaining time.
     * This snapshot does not indicate whether the player is currently allowed to chop.
     */
    public record PlayerPolicy(
            String group, boolean unlimited, int maxUsesPerDay, int maxBlocksPerDay, int cooldownSeconds) {}

    /**
     * Resolves current permissions against the active configuration without loading player data.
     * Call on the main thread on Paper, or the player's owning execution context on Folia.
     * Permission changes and successful config reloads are reflected on the next call.
     *
     * @param player non-null online player
     * @return an immutable policy, or empty if the player is offline or the plugin/config is unavailable
     * @throws IllegalStateException when called outside the required execution context
     */
    public Optional<PlayerPolicy> getPlayerPolicy(Player player) {
        Objects.requireNonNull(player, "player");
        if (!active || !plugin.isEnabled()) return Optional.empty();
        Config config = plugin.getPluginConfig();
        if (config == null) return Optional.empty();
        if (AutoTreeChop.isFolia() ? !RegionAccess.owns(player) : !Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("getPlayerPolicy must run in the player's owning execution context");
        }
        if (!player.isOnline()) return Optional.empty();
        var policy = config.resolvePolicy(player);
        return Optional.of(new PlayerPolicy(
                policy.group(),
                !policy.limitUsage(),
                policy.maxUsesPerDay(),
                policy.maxBlocksPerDay(),
                policy.cooldownSeconds()));
    }

    /**
     * Returns a snapshot, or empty when player data/the plugin is unavailable.
     * Does not expose mutable internal PlayerConfig or database objects.
     *
     * @param playerUUID non-null player UUID
     * @return current state, distinguishing unavailable data from disabled/zero usage
     */
    public Optional<PlayerState> getPlayerState(UUID playerUUID) {
        Objects.requireNonNull(playerUUID, "playerUUID");
        DataManager manager = plugin.getDataManager();
        if (manager == null) return Optional.empty();
        synchronized (manager) {
            if (!active || !plugin.isEnabled()) return Optional.empty();
            PlayerConfig config = manager.getPlayerConfig(playerUUID);
            if (config == null) return Optional.empty();
            synchronized (config) {
                return Optional.of(new PlayerState(
                        config.isAutoTreeChopEnabled(), config.getDailyUses(), config.getDailyBlocksBroken()));
            }
        }
    }

    /** Returns whether a state snapshot is available now; re-check the result of later mutations. */
    public boolean isPlayerDataReady(UUID playerUUID) {
        return getPlayerState(playerUUID).isPresent();
    }

    /**
     * Sets a loaded player's preference atomically with respect to quit/save processing.
     * Disabling also clears pending confirmations. Existing chopping jobs are not cancelled.
     * This is a privileged integration operation: the caller must authorize its own commands.
     * Actual chopping still uses AutoTreeChop's permission, quota, cooldown and protection checks.
     *
     * @param playerUUID non-null player UUID
     * @param enabled desired preference
     * @return UPDATED, UNCHANGED, or UNAVAILABLE; never queues a toggle for a future login
     */
    public ChangeResult setAutoTreeChopEnabled(UUID playerUUID, boolean enabled) {
        Objects.requireNonNull(playerUUID, "playerUUID");
        DataManager manager = plugin.getDataManager();
        if (manager == null) return ChangeResult.UNAVAILABLE;
        synchronized (manager) {
            if (!active || !plugin.isEnabled()) return ChangeResult.UNAVAILABLE;
            PlayerConfig config = manager.getPlayerConfig(playerUUID);
            if (config == null) return ChangeResult.UNAVAILABLE;
            synchronized (config) {
                boolean changed = config.isAutoTreeChopEnabled() != enabled;
                config.setAutoTreeChopEnabled(enabled);
                if (!enabled) plugin.getConfirmationManager().clearPlayer(playerUUID);
                return changed ? ChangeResult.UPDATED : ChangeResult.UNCHANGED;
            }
        }
    }

    void deactivate() {
        active = false;
    }

    /** Legacy convenience method: unavailable data returns false. Prefer getPlayerState(UUID). */
    public boolean isAutoTreeChopEnabled(Player player) {
        return getPlayerState(Objects.requireNonNull(player, "player").getUniqueId())
                .map(PlayerState::enabled)
                .orElse(false);
    }

    /** Legacy setter: unavailable data is a no-op. Prefer setAutoTreeChopEnabled(UUID, boolean). */
    public void enableAutoTreeChop(Player player) {
        setAutoTreeChopEnabled(Objects.requireNonNull(player, "player").getUniqueId(), true);
    }

    /** Legacy setter: unavailable data is a no-op. Prefer setAutoTreeChopEnabled(UUID, boolean). */
    public void disableAutoTreeChop(Player player) {
        setAutoTreeChopEnabled(Objects.requireNonNull(player, "player").getUniqueId(), false);
    }

    /** Legacy counter: unavailable data returns zero. Prefer getPlayerState(UUID). */
    public int getPlayerDailyUses(UUID playerUUID) {
        return getPlayerState(playerUUID).map(PlayerState::dailyUses).orElse(0);
    }

    /** Legacy counter: unavailable data returns zero. Prefer getPlayerState(UUID). */
    public int getPlayerDailyBlocksBroken(UUID playerUUID) {
        return getPlayerState(playerUUID).map(PlayerState::dailyBlocksBroken).orElse(0);
    }
}
