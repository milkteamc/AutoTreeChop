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
import static org.mockito.Mockito.*;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.milkteamc.autotreechop.PlayerPreferences;
import org.milkteamc.autotreechop.PlayerPreferences.Activation;
import org.milkteamc.autotreechop.PlayerPreferences.Toggle;

class PlayerPreferencesStorageTest {
    @TempDir
    Path temp;

    private final UUID uuid = UUID.randomUUID();

    private Connection connection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + temp.resolve("player_data.db"));
    }

    private Plugin plugin() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        return plugin;
    }

    private DatabaseManager open() {
        return new DatabaseManager(plugin(), false, "", 0, "", "", "");
    }

    @Test
    void upgradesExistingRowsPreservesCountersAndPersistsOverridesAcrossReopen() throws Exception {
        try (var conn = connection();
                var statement = conn.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE player_data (uuid VARCHAR(36) PRIMARY KEY, autoTreeChopEnabled BOOLEAN, dailyUses INT, dailyBlocksBroken INT, lastUseDate VARCHAR(10))");
            try (var insert = conn.prepareStatement("INSERT INTO player_data VALUES (?, true, 7, 80, '2026-01-02')")) {
                insert.setString(1, uuid.toString());
                insert.executeUpdate();
            }
        }
        var preferences = new PlayerPreferences(Activation.HOTKEY, Toggle.ON, Toggle.OFF, Toggle.DEFAULT);
        DatabaseManager database = open();
        try {
            var loaded = database.loadPlayerDataAsync(uuid, false).join();
            assertTrue(loaded.isAutoTreeChopEnabled());
            assertEquals(7, loaded.getDailyUses());
            assertEquals(80, loaded.getDailyBlocksBroken());
            assertEquals(LocalDate.of(2026, 1, 2), loaded.getLastUseDate());
            assertEquals(PlayerPreferences.DEFAULTS, loaded.getPreferences());
            loaded.setPreferences(preferences);
            database.savePlayerDataSync(loaded);
        } finally {
            database.close();
        }
        database = open();
        try {
            var loaded = database.loadPlayerDataAsync(uuid, false).join();
            assertEquals(preferences, loaded.getPreferences());
            assertEquals(7, loaded.getDailyUses());
            assertEquals(80, loaded.getDailyBlocksBroken());
            loaded.setPreferences(PlayerPreferences.DEFAULTS);
            database.savePlayerDataSync(loaded);
            assertEquals(
                    PlayerPreferences.DEFAULTS,
                    database.loadPlayerDataAsync(uuid, false).join().getPreferences());
            try (var conn = connection();
                    var statement = conn.createStatement();
                    var result = statement.executeQuery(
                            "SELECT activationPreference, sneakMessagesPreference, leafRemovalPreference, autoReplantPreference FROM player_data")) {
                assertTrue(result.next());
                for (int index = 1; index <= 4; index++) assertNull(result.getObject(index));
            }
        } finally {
            database.close();
        }
    }

    @Test
    void resumesAnAdditiveMigrationWithoutReplacingExistingPreferences() throws Exception {
        try (var conn = connection();
                var statement = conn.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE player_data (uuid VARCHAR(36) PRIMARY KEY, autoTreeChopEnabled BOOLEAN, dailyUses INT, dailyBlocksBroken INT, lastUseDate VARCHAR(10), activationPreference VARCHAR(24))");
            try (var insert =
                    conn.prepareStatement("INSERT INTO player_data VALUES (?, false, 3, 15, '2026-01-02', 'SNEAK')")) {
                insert.setString(1, uuid.toString());
                insert.executeUpdate();
            }
        }
        var database = open();
        try {
            var loaded = database.loadPlayerDataAsync(uuid, true).join();
            assertEquals(PlayerPreferences.DEFAULTS.withActivation(Activation.SNEAK), loaded.getPreferences());
            assertEquals(3, loaded.getDailyUses());
        } finally {
            database.close();
        }
    }

    @Test
    void newPlayersDefaultToServerSettingsAndSnapshotsAreDetached() {
        var database = open();
        try {
            var data = database.loadPlayerDataAsync(uuid, true).join();
            assertEquals(PlayerPreferences.DEFAULTS, data.getPreferences());
            var preferences =
                    new PlayerPreferences(Activation.COMMAND_AND_SNEAK, Toggle.DEFAULT, Toggle.ON, Toggle.OFF);
            data.setPreferences(preferences);
            var pending = database.savePlayerDataBatchAsync(java.util.Map.of(uuid, data));
            data.setPreferences(PlayerPreferences.DEFAULTS);
            pending.join();
            assertEquals(
                    preferences, database.loadPlayerDataAsync(uuid, true).join().getPreferences());
        } finally {
            database.close();
        }
    }

    @Test
    void schemaFailureStopsInitializationAndClosesThePool() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeUpdate(anyString())).thenThrow(new SQLException("read only"));
        try (var pools = mockConstruction(HikariDataSource.class, (source, context) -> {
            when(source.getConnection()).thenReturn(connection);
        })) {
            assertThrows(IllegalStateException.class, this::open);
            verify(pools.constructed().get(0)).close();
        }
    }
}
