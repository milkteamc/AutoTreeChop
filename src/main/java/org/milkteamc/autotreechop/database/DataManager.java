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
 
package org.milkteamc.autotreechop.database;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.PlayerConfig;
import org.milkteamc.autotreechop.tasks.PlayerDataSaveTask;
import org.milkteamc.autotreechop.utils.ConfirmationManager;
import org.milkteamc.autotreechop.utils.SessionManager;

public class DataManager {

    private static final long SAVE_INTERVAL = 1200L; // 60s
    private static final int SAVE_THRESHOLD = 15;

    private final AutoTreeChop plugin;
    private final DatabaseManager databaseManager;
    private final ConfirmationManager confirmationManager;
    private final Map<UUID, PlayerConfig> playerConfigs = new ConcurrentHashMap<>();

    private final Map<UUID, Object> loadSessions = new HashMap<>();

    private PlayerDataSaveTask saveTask;
    private boolean shuttingDown;

    public DataManager(AutoTreeChop plugin, DatabaseManager databaseManager, ConfirmationManager confirmationManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.confirmationManager = confirmationManager;
    }

    public void startSaveTask() {
        this.saveTask = new PlayerDataSaveTask(plugin, SAVE_THRESHOLD);
        saveTask.runTaskTimerAsynchronously(plugin, SAVE_INTERVAL, SAVE_INTERVAL);
    }

    public void shutdown() {
        synchronized (this) {
            shuttingDown = true;
            loadSessions.clear();
            plugin.getLogger().info("Saving all player data before shutdown...");
            if (saveTask != null) saveTask.cancel();

            SessionManager sessionManager = SessionManager.getInstance();
            Map<UUID, DatabaseManager.PlayerData> finalData = new HashMap<>();
            for (Map.Entry<UUID, PlayerConfig> entry : playerConfigs.entrySet()) {
                UUID uuid = entry.getKey();
                if (confirmationManager != null) confirmationManager.clearPlayer(uuid);
                if (sessionManager != null) sessionManager.clearAllPlayerSessions(uuid);
                DatabaseManager.PlayerData snapshot = entry.getValue().popSnapshotIfDirty();
                if (snapshot != null) finalData.put(uuid, snapshot);
            }
            // close() waits for queued work, retries retained failures, then closes the pool.
            databaseManager.savePlayerDataBatchAsync(finalData);
        }
        try {
            databaseManager.close();
            plugin.getLogger().info("All queued player data saved successfully.");
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Player data remains unsaved at shutdown: " + e.getMessage());
        } finally {
            playerConfigs.clear();
        }
    }

    /** Snapshot extraction and submission share a lock with quit and shutdown. */
    public synchronized CompletableFuture<Void> saveDirtyPlayerData() {
        if (shuttingDown) return CompletableFuture.completedFuture(null);
        Map<UUID, DatabaseManager.PlayerData> snapshots = new HashMap<>();
        for (PlayerConfig config : playerConfigs.values()) {
            DatabaseManager.PlayerData data = config.popSnapshotIfDirty();
            if (data != null) snapshots.put(data.getPlayerUUID(), data);
        }
        return databaseManager.savePlayerDataBatchAsync(snapshots);
    }

    public synchronized CompletableFuture<Void> saveAndRemovePlayer(UUID uuid) {
        if (shuttingDown) return CompletableFuture.completedFuture(null);
        loadSessions.remove(uuid);
        PlayerConfig config = playerConfigs.remove(uuid);
        DatabaseManager.PlayerData data = config == null ? null : config.popSnapshotIfDirty();
        return databaseManager.savePlayerDataBatchAsync(data == null ? Map.of() : Map.of(uuid, data));
    }

    /** Only the current login may publish its asynchronously loaded data. */
    public synchronized CompletableFuture<Void> loadPlayerConfig(UUID uuid, boolean defaultTreeChop) {
        if (shuttingDown) return CompletableFuture.completedFuture(null);
        Object session = new Object();
        loadSessions.put(uuid, session);
        return databaseManager.loadPlayerDataAsync(uuid, defaultTreeChop).thenAccept(data -> {
            synchronized (this) {
                if (shuttingDown || loadSessions.get(uuid) != session) return;
                PlayerConfig config = new PlayerConfig(uuid, data);
                if (config.isAutoTreeChopEnabled() && confirmationManager != null) {
                    confirmationManager.markRejoin(uuid);
                }
                playerConfigs.put(uuid, config);
                loadSessions.remove(uuid);
            }
        });
    }

    public synchronized void addPlayerConfig(UUID uuid, PlayerConfig config) {
        if (shuttingDown) return;
        playerConfigs.put(uuid, config);
    }

    public synchronized PlayerConfig removePlayerConfig(UUID uuid) {
        loadSessions.remove(uuid);
        return playerConfigs.remove(uuid);
    }

    public PlayerConfig getPlayerConfig(UUID uuid) {
        return playerConfigs.get(uuid);
    }

    public Collection<PlayerConfig> getOnlinePlayersConfigs() {
        return playerConfigs.values();
    }

    public int getPlayerDailyUses(UUID playerUUID) {
        PlayerConfig config = getPlayerConfig(playerUUID);
        return config != null ? config.getDailyUses() : 0;
    }

    public int getPlayerDailyBlocksBroken(UUID playerUUID) {
        PlayerConfig config = getPlayerConfig(playerUUID);
        return config != null ? config.getDailyBlocksBroken() : 0;
    }
}
