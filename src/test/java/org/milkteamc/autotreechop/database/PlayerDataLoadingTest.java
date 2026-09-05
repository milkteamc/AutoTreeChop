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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.milkteamc.autotreechop.database.DatabaseManager.PlayerData;

class PlayerDataLoadingTest {
    private final UUID uuid = UUID.randomUUID();
    private final DatabaseManager database = mock(DatabaseManager.class);
    private final DataManager manager = new DataManager(null, database, null);

    private PlayerData data(int uses) {
        return new PlayerData(uuid, true, uses, 40, LocalDate.now());
    }

    @Test
    void databaseReadFailurePropagatesInsteadOfReturningDefaults() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(new File("build/test-data"));
        Connection schemaConnection = mock(Connection.class);
        when(schemaConnection.createStatement()).thenReturn(mock(Statement.class));
        try (var sources = mockConstruction(HikariDataSource.class, (source, context) -> {
            when(source.getConnection()).thenReturn(schemaConnection).thenThrow(new SQLException("offline"));
        })) {
            DatabaseManager realDatabase = new DatabaseManager(plugin, false, "", 0, "", "", "");
            try {
                CompletionException failure = assertThrows(
                        CompletionException.class,
                        () -> realDatabase.loadPlayerDataAsync(uuid, true).join());
                assertInstanceOf(SQLException.class, failure.getCause());
            } finally {
                realDatabase.close();
            }
        }
    }

    @Test
    void configIsUnavailableUntilLoadSucceeds() {
        var load = new CompletableFuture<PlayerData>();
        when(database.loadPlayerDataAsync(uuid, false)).thenReturn(load);
        var completion = manager.loadPlayerConfig(uuid, false);
        assertNull(manager.getPlayerConfig(uuid));
        load.complete(data(4));
        completion.join();
        assertEquals(4, manager.getPlayerConfig(uuid).getDailyUses());
        assertFalse(manager.getPlayerConfig(uuid).isDirty());
    }

    @Test
    void failedLoadNeverCreatesOrSavesDefaultConfig() {
        when(database.loadPlayerDataAsync(uuid, true))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Database offline")));
        when(database.savePlayerDataBatchAsync(anyMap())).thenReturn(CompletableFuture.completedFuture(null));
        assertThrows(CompletionException.class, () -> manager.loadPlayerConfig(uuid, true)
                .join());
        assertNull(manager.getPlayerConfig(uuid));
        manager.saveDirtyPlayerData().join();
        verify(database).savePlayerDataBatchAsync(argThat(java.util.Map::isEmpty));
    }

    @Test
    void quittingInvalidatesInFlightLoad() {
        var load = new CompletableFuture<PlayerData>();
        when(database.loadPlayerDataAsync(uuid, false)).thenReturn(load);
        when(database.savePlayerDataBatchAsync(anyMap())).thenReturn(CompletableFuture.completedFuture(null));
        var completion = manager.loadPlayerConfig(uuid, false);
        manager.saveAndRemovePlayer(uuid).join();
        load.complete(data(4));
        completion.join();
        assertNull(manager.getPlayerConfig(uuid));
    }

    @Test
    void oldLoginCannotOverwriteNewLogin() {
        var oldLoad = new CompletableFuture<PlayerData>();
        var newLoad = new CompletableFuture<PlayerData>();
        when(database.loadPlayerDataAsync(uuid, false)).thenReturn(oldLoad, newLoad);
        var oldCompletion = manager.loadPlayerConfig(uuid, false);
        manager.removePlayerConfig(uuid);
        var newCompletion = manager.loadPlayerConfig(uuid, false);
        newLoad.complete(data(9));
        newCompletion.join();
        oldLoad.complete(data(2));
        oldCompletion.join();
        assertEquals(9, manager.getPlayerConfig(uuid).getDailyUses());
    }

    @Test
    void reconnectCanRecoverFromLoadFailure() {
        when(database.loadPlayerDataAsync(uuid, false))
                .thenReturn(
                        CompletableFuture.failedFuture(new IllegalStateException("offline")),
                        CompletableFuture.completedFuture(data(7)));
        assertThrows(CompletionException.class, () -> manager.loadPlayerConfig(uuid, false)
                .join());
        manager.removePlayerConfig(uuid);
        manager.loadPlayerConfig(uuid, false).join();
        assertEquals(7, manager.getPlayerConfig(uuid).getDailyUses());
    }
}
