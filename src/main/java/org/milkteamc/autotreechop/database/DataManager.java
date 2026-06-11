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

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    private PlayerDataSaveTask saveTask;

    public DataManager(AutoTreeChop plugin, DatabaseManager databaseManager, ConfirmationManager confirmationManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.confirmationManager = confirmationManager;
    }

    public void startSaveTask() {
        this.saveTask = new PlayerDataSaveTask(plugin, SAVE_THRESHOLD);
        UniversalScheduler.getScheduler(plugin).runTaskTimerAsynchronously(saveTask, SAVE_INTERVAL, SAVE_INTERVAL);
    }

    public void shutdown() {
        plugin.getLogger().info("Saving all player data before shutdown...");

        if (saveTask != null) {
            try {
                saveTask.cancel();
            } catch (IllegalStateException ignored) {
                // Task was never scheduled or already cancelled (e.g. Folia shutdown)
            }
        }

        if (!playerConfigs.isEmpty()) {
            SessionManager sessionManager = SessionManager.getInstance();
            List<DatabaseManager.PlayerData> dirtyDataList = new ArrayList<>();

            for (Map.Entry<UUID, PlayerConfig> entry : playerConfigs.entrySet()) {
                UUID uuid = entry.getKey();
                PlayerConfig pConfig = entry.getValue();

                if (confirmationManager != null) {
                    confirmationManager.clearPlayer(uuid);
                }

                if (sessionManager != null) {
                    sessionManager.clearAllPlayerSessions(uuid);
                }

                DatabaseManager.PlayerData snapshot = pConfig.popSnapshotIfDirty();
                if (snapshot != null) {
                    dirtyDataList.add(snapshot);
                }
            }

            if (!dirtyDataList.isEmpty() && databaseManager != null) {
                long startTime = System.currentTimeMillis();
                databaseManager.savePlayerDataBatchSync(dirtyDataList);
                long duration = System.currentTimeMillis() - startTime;
                plugin.getLogger()
                        .info("Successfully saved " + dirtyDataList.size() + " player records in " + duration + "ms.");
            }

            playerConfigs.clear();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    public void addPlayerConfig(UUID uuid, PlayerConfig config) {
        playerConfigs.put(uuid, config);
    }

    public PlayerConfig removePlayerConfig(UUID uuid) {
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
