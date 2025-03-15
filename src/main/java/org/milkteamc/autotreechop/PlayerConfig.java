package org.milkteamc.autotreechop;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.time.LocalDate;
import java.util.UUID;

import static org.bukkit.Bukkit.getLogger;

public class PlayerConfig {

    private final UUID playerUUID;
    private final Connection connection;
    private boolean autoTreeChopEnabled;
    private int dailyUses;
    private int dailyBlocksBroken;
    private LocalDate lastUseDate;

    public PlayerConfig(UUID playerUUID, boolean useMysql, String hostname, String database, int port, String username, String password, boolean defaultTreeChop) {
        this.playerUUID = playerUUID;
        this.connection = establishConnection(useMysql, hostname, port, database, username, password);
        createTable();
        loadConfig(defaultTreeChop);
    }

    private Connection establishConnection(boolean useMysql, String hostname, int port, String database, String username, String password) {
        if (useMysql) {
            try {
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl("jdbc:mysql://" + hostname + ":" + port + "/" + database);
                config.setUsername(username);
                config.setPassword(password);
                config.setMaximumPoolSize(10);

                HikariDataSource dataSource = new HikariDataSource(config);
                return dataSource.getConnection();
            } catch (Exception e) {
                getLogger().warning("Error establishing MySQL connection: " + e.getMessage());
                return null;
            }
        } else {
            try {
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl("jdbc:sqlite:plugins/AutoTreeChop/player_data.db");
                config.setMaximumPoolSize(10);

                HikariDataSource dataSource = new HikariDataSource(config);
                return dataSource.getConnection();
            } catch (Exception e) {
                getLogger().warning("Error establishing SQLite connection: " + e.getMessage());
                return null;
            }
        }
    }

    private void createTable() {
        try (PreparedStatement statement = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS player_data (" +
                        "uuid VARCHAR(36) PRIMARY KEY," +
                        "autoTreeChopEnabled BOOLEAN," +
                        "dailyUses INT," +
                        "dailyBlocksBroken INT," +
                        "lastUseDate VARCHAR(10));")) {
            statement.executeUpdate();
        } catch (SQLException e) {
            getLogger().warning("Error creating database table: " + e.getMessage());
        }
    }

    private void loadConfig(boolean defaultTreeChop) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM player_data WHERE uuid = ?")) {
            statement.setString(1, playerUUID.toString());
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                autoTreeChopEnabled = resultSet.getBoolean("autoTreeChopEnabled");
                dailyUses = resultSet.getInt("dailyUses");
                dailyBlocksBroken = resultSet.getInt("dailyBlocksBroken");
                lastUseDate = LocalDate.parse(resultSet.getString("lastUseDate"));
            } else {
                autoTreeChopEnabled = defaultTreeChop;
                dailyUses = 0;
                dailyBlocksBroken = 0;
                lastUseDate = LocalDate.now();

                try (PreparedStatement insertStatement = connection.prepareStatement(
                        "INSERT INTO player_data (uuid, autoTreeChopEnabled, dailyUses, dailyBlocksBroken, lastUseDate) VALUES (?, ?, ?, ?, ?)")) {
                    insertStatement.setString(1, playerUUID.toString());
                    insertStatement.setBoolean(2, autoTreeChopEnabled);
                    insertStatement.setInt(3, dailyUses);
                    insertStatement.setInt(4, dailyBlocksBroken);
                    insertStatement.setString(5, lastUseDate.toString());
                    insertStatement.executeUpdate();
                } catch (SQLException e) {
                    getLogger().warning("Error inserting player data into database: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            getLogger().warning("Error loading player data from database: " + e.getMessage());
        }
    }

    public boolean isAutoTreeChopEnabled() {
        return autoTreeChopEnabled;
    }

    public void setAutoTreeChopEnabled(boolean enabled) {
        this.autoTreeChopEnabled = enabled;
        updateConfig();
    }

    public int getDailyUses() {
        checkAndUpdateDate();
        return dailyUses;
    }

    public void incrementDailyUses() {
        checkAndUpdateDate();
        dailyUses++;
        updateConfig();
    }

    public int getDailyBlocksBroken() {
        checkAndUpdateDate();
        return dailyBlocksBroken;
    }

    private void initializeSQLiteTables(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            String createTableQuery = "CREATE TABLE IF NOT EXISTS player_data (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "autoTreeChopEnabled BOOLEAN," +
                    "dailyUses INT," +
                    "dailyBlocksBroken INT," +
                    "lastUseDate VARCHAR(10)" +
                    ")";
            statement.executeUpdate(createTableQuery);
        } catch (SQLException e) {
            getLogger().warning("Error initializing database tables: " + e.getMessage());
        }
    }

    public void incrementDailyBlocksBroken() {
        checkAndUpdateDate();
        dailyBlocksBroken++;
        updateConfig();
    }

    private void checkAndUpdateDate() {
        if (!lastUseDate.equals(LocalDate.now())) {
            dailyUses = 0;
            dailyBlocksBroken = 0;
            lastUseDate = LocalDate.now();
            updateConfig();
        }
    }

    private void updateConfig() {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE player_data SET autoTreeChopEnabled = ?, dailyUses = ?, dailyBlocksBroken = ?, lastUseDate = ? WHERE uuid = ?")) {
            statement.setBoolean(1, autoTreeChopEnabled);
            statement.setInt(2, dailyUses);
            statement.setInt(3, dailyBlocksBroken);
            statement.setString(4, lastUseDate.toString());
            statement.setString(5, playerUUID.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            getLogger().warning("Error updating player data in database: " + e.getMessage());
        }
    }
}