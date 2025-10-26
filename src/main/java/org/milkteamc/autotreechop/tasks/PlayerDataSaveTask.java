package org.milkteamc.autotreechop.tasks;

import org.bukkit.scheduler.BukkitRunnable;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.PlayerConfig;
import org.milkteamc.autotreechop.database.DatabaseManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataSaveTask extends BukkitRunnable {

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
        for (PlayerConfig config : plugin.getAllPlayerConfigs().values()) {
            if (config.isDirty()) {
                count++;
            }
        }
        return count;
    }

    private void saveAllDirtyData() {
        Map<UUID, DatabaseManager.PlayerData> dirtyDataMap = new HashMap<>();

        for (Map.Entry<UUID, PlayerConfig> entry : plugin.getAllPlayerConfigs().entrySet()) {
            PlayerConfig config = entry.getValue();
            if (config.isDirty()) {
                dirtyDataMap.put(entry.getKey(), config.getData());
                config.clearDirty();
            }
        }

        if (!dirtyDataMap.isEmpty()) {
            plugin.getDatabaseManager().savePlayerDataBatchAsync(dirtyDataMap)
                    .thenRun(() -> {
                        dirtyCount = 0;
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("Failed to save player data: " + ex.getMessage());
                        for (UUID uuid : dirtyDataMap.keySet()) {
                            PlayerConfig config = plugin.getPlayerConfig(uuid);
                            if (config != null) {
                                config.markDirty();
                            }
                        }
                        return null;
                    });
        }
    }
}