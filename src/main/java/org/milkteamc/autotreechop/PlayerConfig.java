package org.milkteamc.autotreechop;

import org.milkteamc.autotreechop.database.DatabaseManager;

import java.time.LocalDate;
import java.util.UUID;

public class PlayerConfig {

    private final UUID playerUUID;
    private final DatabaseManager.PlayerData data;
    private boolean dirty = false;

    public PlayerConfig(UUID playerUUID, DatabaseManager.PlayerData data) {
        this.playerUUID = playerUUID;
        this.data = data;
    }

    private void checkAndUpdateDate() {
        if (!data.getLastUseDate().equals(LocalDate.now())) {
            data.setDailyUses(0);
            data.setDailyBlocksBroken(0);
            data.setLastUseDate(LocalDate.now());
            markDirty();
        }
    }

    public boolean isAutoTreeChopEnabled() {
        return data.isAutoTreeChopEnabled();
    }

    public void setAutoTreeChopEnabled(boolean enabled) {
        if (data.isAutoTreeChopEnabled() != enabled) {
            data.setAutoTreeChopEnabled(enabled);
            markDirty();
        }
    }

    public int getDailyUses() {
        checkAndUpdateDate();
        return data.getDailyUses();
    }

    public void incrementDailyUses() {
        checkAndUpdateDate();
        data.incrementDailyUses();
        markDirty();
    }

    public int getDailyBlocksBroken() {
        checkAndUpdateDate();
        return data.getDailyBlocksBroken();
    }

    public void incrementDailyBlocksBroken() {
        checkAndUpdateDate();
        data.incrementDailyBlocksBroken();
        markDirty();
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public boolean isDirty() {
        return dirty;
    }

    public DatabaseManager.PlayerData getData() {
        return data;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }
}