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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.bukkit.plugin.Plugin;
import org.milkteamc.autotreechop.PlayerPreferences;

public class DatabaseManager {

    private final Plugin plugin;
    private final HikariDataSource dataSource;
    private final boolean useMysql;
    private final PlayerDataWriteQueue writes;

    public DatabaseManager(
            Plugin plugin,
            boolean useMysql,
            String hostname,
            int port,
            String database,
            String username,
            String password) {
        this.plugin = plugin;
        this.useMysql = useMysql;
        this.dataSource = initializeDataSource(useMysql, hostname, port, database, username, password);
        createTable();
        this.writes = new PlayerDataWriteQueue(this::writePlayerDataBatch);
    }

    private HikariDataSource initializeDataSource(
            boolean useMysql, String hostname, int port, String database, String username, String password) {
        HikariConfig config = new HikariConfig();

        if (useMysql) {
            config.setJdbcUrl("jdbc:mysql://" + hostname + ":" + port + "/" + database);
            config.setUsername(username);
            config.setPassword(password);
        } else {
            String dbPath = plugin.getDataFolder().getAbsolutePath() + "/player_data.db";
            config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        }

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        return new HikariDataSource(config);
    }

    private void createTable() {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS player_data ("
                    + "uuid VARCHAR(36) PRIMARY KEY,"
                    + "autoTreeChopEnabled BOOLEAN,"
                    + "dailyUses INT,"
                    + "dailyBlocksBroken INT,"
                    + "lastUseDate VARCHAR(10),"
                    + "activationPreference VARCHAR(24),"
                    + "sneakMessagesPreference BOOLEAN,"
                    + "leafRemovalPreference BOOLEAN,"
                    + "autoReplantPreference BOOLEAN)");
            Set<String> columns = new HashSet<>();
            try (ResultSet result = stmt.executeQuery("SELECT * FROM player_data WHERE 1 = 0")) {
                var metadata = result.getMetaData();
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    columns.add(metadata.getColumnName(index).toLowerCase(Locale.ROOT));
                }
            }
            for (String definition : List.of(
                    "activationPreference VARCHAR(24)",
                    "sneakMessagesPreference BOOLEAN",
                    "leafRemovalPreference BOOLEAN",
                    "autoReplantPreference BOOLEAN")) {
                String name = definition.substring(0, definition.indexOf(' '));
                if (!columns.contains(name.toLowerCase(Locale.ROOT))) {
                    stmt.executeUpdate("ALTER TABLE player_data ADD COLUMN " + definition);
                }
            }
        } catch (SQLException e) {
            dataSource.close();
            throw new IllegalStateException("Could not initialize player data schema", e);
        }
    }

    public CompletableFuture<PlayerData> loadPlayerDataAsync(UUID playerUUID, boolean defaultTreeChop) {
        return writes.read(() -> {
            PlayerData pending = writes.getPending(playerUUID);
            if (pending != null) return pending;
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement stmt = conn.prepareStatement("SELECT * FROM player_data WHERE uuid = ?")) {

                stmt.setString(1, playerUUID.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new PlayerData(
                                playerUUID,
                                rs.getBoolean("autoTreeChopEnabled"),
                                rs.getInt("dailyUses"),
                                rs.getInt("dailyBlocksBroken"),
                                LocalDate.parse(rs.getString("lastUseDate")),
                                readPreferences(rs));
                    } else {
                        PlayerData data = new PlayerData(playerUUID, defaultTreeChop, 0, 0, LocalDate.now());
                        insertPlayerData(data);
                        return data;
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException("Failed to load player data for " + playerUUID, e);
            }
        });
    }

    public void savePlayerDataSync(PlayerData data) {
        savePlayerDataBatchSync(List.of(data));
    }

    public void savePlayerDataBatchSync(Collection<PlayerData> dataCollection) {
        writes.save(dataCollection).join();
    }

    private void writePlayerDataBatch(Collection<PlayerData> dataCollection) {

        if (dataCollection == null || dataCollection.isEmpty()) return;

        String sql = buildUpsertSql();

        try (Connection conn = dataSource.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (PlayerData data : dataCollection) {
                    bindUpsertParams(stmt, data);
                    stmt.addBatch();
                }

                stmt.executeBatch();
                conn.commit();

            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            throw new CompletionException("Failed to save player data", e);
        }
    }

    public CompletableFuture<Void> savePlayerDataBatchAsync(Map<UUID, PlayerData> dataMap) {
        return writes.save(dataMap.values());
    }

    /**
     * Returns a dialect-appropriate UPSERT statement.
     *
     * <ul>
     * <li>SQLite: {@code INSERT OR REPLACE INTO ...}
     * <li>MySQL:  {@code INSERT INTO ... ON DUPLICATE KEY UPDATE ...}
     * </ul>
     */
    private String buildUpsertSql() {
        if (useMysql) {
            return "INSERT INTO player_data (uuid, autoTreeChopEnabled, dailyUses, dailyBlocksBroken, lastUseDate, activationPreference, sneakMessagesPreference, leafRemovalPreference, autoReplantPreference) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "autoTreeChopEnabled = VALUES(autoTreeChopEnabled), "
                    + "dailyUses = VALUES(dailyUses), "
                    + "dailyBlocksBroken = VALUES(dailyBlocksBroken), "
                    + "lastUseDate = VALUES(lastUseDate), "
                    + "activationPreference = VALUES(activationPreference), "
                    + "sneakMessagesPreference = VALUES(sneakMessagesPreference), "
                    + "leafRemovalPreference = VALUES(leafRemovalPreference), "
                    + "autoReplantPreference = VALUES(autoReplantPreference)";
        } else {
            // SQLite: INSERT OR REPLACE replaces the entire row when the PK conflicts.
            return "INSERT OR REPLACE INTO player_data "
                    + "(uuid, autoTreeChopEnabled, dailyUses, dailyBlocksBroken, lastUseDate, activationPreference, sneakMessagesPreference, leafRemovalPreference, autoReplantPreference) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }
    }

    /** Binds the UPSERT parameters in the order declared by {@link #buildUpsertSql()}. */
    private void bindUpsertParams(PreparedStatement stmt, PlayerData data) throws SQLException {
        stmt.setString(1, data.getPlayerUUID().toString());
        stmt.setBoolean(2, data.isAutoTreeChopEnabled());
        stmt.setInt(3, data.getDailyUses());
        stmt.setInt(4, data.getDailyBlocksBroken());
        stmt.setString(5, data.getLastUseDate().toString());
        PlayerPreferences preferences = data.getPreferences();
        if (preferences.activation() == PlayerPreferences.Activation.DEFAULT) stmt.setNull(6, Types.VARCHAR);
        else stmt.setString(6, preferences.activation().name());
        bindToggle(stmt, 7, preferences.sneakMessages());
        bindToggle(stmt, 8, preferences.leafRemoval());
        bindToggle(stmt, 9, preferences.autoReplant());
    }

    private static void bindToggle(PreparedStatement statement, int index, PlayerPreferences.Toggle value)
            throws SQLException {
        if (value == PlayerPreferences.Toggle.DEFAULT) statement.setNull(index, Types.BOOLEAN);
        else statement.setBoolean(index, value == PlayerPreferences.Toggle.ON);
    }

    private static PlayerPreferences.Toggle readToggle(ResultSet result, String column) throws SQLException {
        boolean value = result.getBoolean(column);
        return result.wasNull()
                ? PlayerPreferences.Toggle.DEFAULT
                : value ? PlayerPreferences.Toggle.ON : PlayerPreferences.Toggle.OFF;
    }

    private static PlayerPreferences readPreferences(ResultSet result) throws SQLException {
        String mode = result.getString("activationPreference");
        try {
            return new PlayerPreferences(
                    mode == null ? PlayerPreferences.Activation.DEFAULT : PlayerPreferences.Activation.valueOf(mode),
                    readToggle(result, "sneakMessagesPreference"),
                    readToggle(result, "leafRemovalPreference"),
                    readToggle(result, "autoReplantPreference"));
        } catch (IllegalArgumentException e) {
            throw new SQLException("Invalid saved activation preference", e);
        }
    }

    private void insertPlayerData(PlayerData data) throws SQLException {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO player_data (uuid, autoTreeChopEnabled, dailyUses, "
                                + "dailyBlocksBroken, lastUseDate, activationPreference, sneakMessagesPreference, leafRemovalPreference, autoReplantPreference) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

            bindUpsertParams(stmt, data);
            stmt.executeUpdate();
        }
    }

    public void close() {
        try {
            writes.close();
        } finally {
            dataSource.close();
        }
    }

    public static class PlayerData {
        private final UUID playerUUID;
        private boolean autoTreeChopEnabled;
        private int dailyUses;
        private int dailyBlocksBroken;
        private LocalDate lastUseDate;
        private PlayerPreferences preferences;

        public PlayerData(
                UUID playerUUID,
                boolean autoTreeChopEnabled,
                int dailyUses,
                int dailyBlocksBroken,
                LocalDate lastUseDate) {
            this(
                    playerUUID,
                    autoTreeChopEnabled,
                    dailyUses,
                    dailyBlocksBroken,
                    lastUseDate,
                    PlayerPreferences.DEFAULTS);
        }

        public PlayerData(
                UUID playerUUID,
                boolean autoTreeChopEnabled,
                int dailyUses,
                int dailyBlocksBroken,
                LocalDate lastUseDate,
                PlayerPreferences preferences) {
            this.preferences = Objects.requireNonNull(preferences, "preferences");
            this.playerUUID = playerUUID;
            this.autoTreeChopEnabled = autoTreeChopEnabled;
            this.dailyUses = dailyUses;
            this.dailyBlocksBroken = dailyBlocksBroken;
            this.lastUseDate = lastUseDate;
        }

        public PlayerData(PlayerData source) {
            this.playerUUID = source.playerUUID;
            this.autoTreeChopEnabled = source.autoTreeChopEnabled;
            this.dailyUses = source.dailyUses;
            this.dailyBlocksBroken = source.dailyBlocksBroken;
            this.lastUseDate = source.lastUseDate;
            this.preferences = source.preferences;
        }

        public PlayerPreferences getPreferences() {
            return preferences;
        }

        public void setPreferences(PlayerPreferences preferences) {
            this.preferences = Objects.requireNonNull(preferences, "preferences");
        }

        public UUID getPlayerUUID() {
            return playerUUID;
        }

        public boolean isAutoTreeChopEnabled() {
            return autoTreeChopEnabled;
        }

        public void setAutoTreeChopEnabled(boolean enabled) {
            this.autoTreeChopEnabled = enabled;
        }

        public int getDailyUses() {
            return dailyUses;
        }

        public void setDailyUses(int uses) {
            this.dailyUses = uses;
        }

        public void incrementDailyUses() {
            this.dailyUses++;
        }

        public int getDailyBlocksBroken() {
            return dailyBlocksBroken;
        }

        public void setDailyBlocksBroken(int blocks) {
            this.dailyBlocksBroken = blocks;
        }

        public void incrementDailyBlocksBroken() {
            this.dailyBlocksBroken++;
        }

        public LocalDate getLastUseDate() {
            return lastUseDate;
        }

        public void setLastUseDate(LocalDate date) {
            this.lastUseDate = date;
        }
    }
}
