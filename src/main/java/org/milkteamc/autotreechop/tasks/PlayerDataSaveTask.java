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
 
package org.milkteamc.autotreechop.tasks;

import com.github.Anon8281.universalScheduler.UniversalRunnable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.PlayerConfig;
import org.milkteamc.autotreechop.database.DatabaseManager;

public class PlayerDataSaveTask extends UniversalRunnable {

    private final AutoTreeChop plugin;
    private final int saveThreshold;
    private int dirtyCount = 0;

    public PlayerDataSaveTask(AutoTreeChop plugin, int saveThreshold) {
        this.plugin = plugin;
        this.saveThreshold = saveThreshold;
    }

    @Override
    public void run() {
        saveAllDirtyData();
    }

    public void checkThreshold() {
        dirtyCount = countDirtyData();
        if (dirtyCount >= saveThreshold) {
            saveAllDirtyData();
        }
    }

    private int countDirtyData() {
        int count = 0;
        for (PlayerConfig config : plugin.getDataManager().getOnlinePlayersConfigs()) {
            if (config.isDirty()) {
                count++;
            }
        }
        return count;
    }

    private void saveAllDirtyData() {
        Map<UUID, DatabaseManager.PlayerData> dirtyDataMap = new HashMap<>();

        for (PlayerConfig config : plugin.getDataManager().getOnlinePlayersConfigs()) {
            if (config.isDirty()) {
                DatabaseManager.PlayerData snapshot = new DatabaseManager.PlayerData(config.getData());

                dirtyDataMap.put(snapshot.getPlayerUUID(), snapshot);
                config.clearDirty();
            }
        }

        if (!dirtyDataMap.isEmpty()) {
            plugin.getDatabaseManager()
                    .savePlayerDataBatchAsync(dirtyDataMap)
                    .thenRun(() -> {
                        dirtyCount = 0;
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("Failed to save player data: " + ex.getMessage());
                        for (UUID uuid : dirtyDataMap.keySet()) {
                            PlayerConfig config = plugin.getDataManager().getPlayerConfig(uuid);
                            if (config != null) {
                                config.markDirty();
                            }
                        }
                        return null;
                    });
        }
    }
}
