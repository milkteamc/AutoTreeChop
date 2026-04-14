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
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.Plugin;

public class DatabaseManager {

    private final Plugin plugin;
    private final HikariDataSource dataSource;
    private final boolean useMysql;

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
                    + "lastUseDate VARCHAR(10))");
        } catch (SQLException e) {
            plugin.getLogger().warning("Error creating database table: " + e.getMessage());
        }
    }

    public CompletableFuture<PlayerData> loadPlayerDataAsync(UUID playerUUID, boolean defaultTreeChop) {
        return CompletableFuture.supplyAsync(() -> {
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
                                LocalDate.parse(rs.getString("lastUseDate")));
                    } else {
                        PlayerData data = new PlayerData(playerUUID, defaultTreeChop, 0, 0, LocalDate.now());
                        insertPlayerData(data);
                        return data;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Error loading player data: " + e.getMessage());
                return new PlayerData(playerUUID, defaultTreeChop, 0, 0, LocalDate.now());
            }
        });
    }

    public void savePlayerDataSync(PlayerData data) {
        String sql = buildUpsertSql();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindUpsertParams(stmt, data);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Error saving player data: " + e.getMessage());
        }
    }

    public void savePlayerDataBatchSync(Collection<PlayerData> dataCollection) {
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
                conn.rollback();
                plugin.getLogger().severe("Failed to batch save player data: " + e.getMessage());
                throw e; // 如果是嚴重錯誤，可能需要往上拋或在這裡單純記錄
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Database connection error during batch save: " + e.getMessage());
        }
    }

    public CompletableFuture<Void> savePlayerDataBatchAsync(Map<UUID, PlayerData> dataMap) {
        return CompletableFuture.runAsync(() -> savePlayerDataBatchSync(dataMap.values()));
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
            return "INSERT INTO player_data (uuid, autoTreeChopEnabled, dailyUses, dailyBlocksBroken, lastUseDate) "
                    + "VALUES (?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "autoTreeChopEnabled = VALUES(autoTreeChopEnabled), "
                    + "dailyUses = VALUES(dailyUses), "
                    + "dailyBlocksBroken = VALUES(dailyBlocksBroken), "
                    + "lastUseDate = VALUES(lastUseDate)";
        } else {
            // SQLite: INSERT OR REPLACE replaces the entire row when the PK conflicts.
            return "INSERT OR REPLACE INTO player_data "
                    + "(uuid, autoTreeChopEnabled, dailyUses, dailyBlocksBroken, lastUseDate) "
                    + "VALUES (?, ?, ?, ?, ?)";
        }
    }

    /** Binds the five UPSERT parameters in the order declared by {@link #buildUpsertSql()}. */
    private void bindUpsertParams(PreparedStatement stmt, PlayerData data) throws SQLException {
        stmt.setString(1, data.getPlayerUUID().toString());
        stmt.setBoolean(2, data.isAutoTreeChopEnabled());
        stmt.setInt(3, data.getDailyUses());
        stmt.setInt(4, data.getDailyBlocksBroken());
        stmt.setString(5, data.getLastUseDate().toString());
    }

    private void insertPlayerData(PlayerData data) throws SQLException {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement("INSERT INTO player_data (uuid, autoTreeChopEnabled, dailyUses, "
                                + "dailyBlocksBroken, lastUseDate) VALUES (?, ?, ?, ?, ?)")) {

            stmt.setString(1, data.getPlayerUUID().toString());
            stmt.setBoolean(2, data.isAutoTreeChopEnabled());
            stmt.setInt(3, data.getDailyUses());
            stmt.setInt(4, data.getDailyBlocksBroken());
            stmt.setString(5, data.getLastUseDate().toString());
            stmt.executeUpdate();
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public static class PlayerData {
        private final UUID playerUUID;
        private boolean autoTreeChopEnabled;
        private int dailyUses;
        private int dailyBlocksBroken;
        private LocalDate lastUseDate;

        public PlayerData(
                UUID playerUUID,
                boolean autoTreeChopEnabled,
                int dailyUses,
                int dailyBlocksBroken,
                LocalDate lastUseDate) {
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
