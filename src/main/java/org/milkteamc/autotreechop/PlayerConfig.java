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

import java.time.LocalDate;
import java.util.UUID;
import org.milkteamc.autotreechop.database.DatabaseManager;

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
