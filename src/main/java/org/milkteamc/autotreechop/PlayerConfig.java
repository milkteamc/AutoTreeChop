package org.milkteamc.autotreechop;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.time.LocalDate;
import java.util.UUID;

import static org.bukkit.Bukkit.getLogger;

class PlayerConfig {

    private final UUID playerUUID;
    private final Connection connection;
    private boolean autoTreeChopEnabled;
    private int dailyUses;
    private int dailyUsesLimit;
    private int dailyBlocksBroken;
    private int dailyBlocksBrokenLimit;
    private LocalDate lastUseDate;

    PlayerConfig(UUID playerUUID,
                        boolean useMysql, String hostname, String database, int port, String username, String password,
                        int defaultDailyUsesLimit, int defaultDailyBlocksBrokenLimit) {
        this.playerUUID = playerUUID;
        this.connection = establishConnection(useMysql, hostname, port, database, username, password);
        alterTable(defaultDailyUsesLimit, defaultDailyBlocksBrokenLimit);
        loadConfig(defaultDailyUsesLimit, defaultDailyBlocksBrokenLimit);
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

    private void alterTable(int defaultDailyUsesLimit, int defaultDailyBlocksBrokenLimit) {
        try (PreparedStatement statement = connection.prepareStatement(
                "ALTER TABLE player_data ADD COLUMN IF NOT EXISTS dailyUsesLimit INT DEFAULT " + defaultDailyUsesLimit + ";"
        )) {
            statement.executeUpdate();
        } catch (SQLException e) {
            getLogger().warning("Error altering database table: " + e.getMessage());
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "ALTER TABLE player_data ADD COLUMN IF NOT EXISTS dailyBlocksBrokenLimit INT DEFAULT" + defaultDailyBlocksBrokenLimit + ";"
        )) {
            statement.executeUpdate();
        } catch (SQLException e) {
            getLogger().warning("Error altering database table: " + e.getMessage());
        }
    }

    private void loadConfig(int defaultDailyUsesLimit, int defaultDailyBlocksBrokenLimit) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM player_data WHERE uuid = ?")) {
            statement.setString(1, playerUUID.toString());
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                autoTreeChopEnabled = resultSet.getBoolean("autoTreeChopEnabled");
                dailyUses = resultSet.getInt("dailyUses");
                dailyBlocksBroken = resultSet.getInt("dailyBlocksBroken");
                lastUseDate = LocalDate.parse(resultSet.getString("lastUseDate"));
                dailyUsesLimit = resultSet.getInt("dailyUsesLimit");
                dailyBlocksBrokenLimit = resultSet.getInt("dailyBlocksBrokenLimit");
            } else {
                autoTreeChopEnabled = false;
                dailyUses = 0;
                dailyBlocksBroken = 0;
                lastUseDate = LocalDate.now();

                dailyUsesLimit = defaultDailyUsesLimit;
                dailyBlocksBrokenLimit = defaultDailyBlocksBrokenLimit;

                try (PreparedStatement insertStatement = connection.prepareStatement(
                        "INSERT INTO player_data (uuid, autoTreeChopEnabled, dailyUses, dailyBlocksBroken, lastUseDate, dailyUsesLimit, dailyBlocksBrokenLimit) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    insertStatement.setString(1, playerUUID.toString());
                    insertStatement.setBoolean(2, autoTreeChopEnabled);
                    insertStatement.setInt(3, dailyUses);
                    insertStatement.setInt(4, dailyBlocksBroken);
                    insertStatement.setString(5, lastUseDate.toString());
                    insertStatement.setInt(6, dailyUsesLimit);
                    insertStatement.setInt(7, dailyBlocksBrokenLimit);
                    insertStatement.executeUpdate();
                } catch (SQLException e) {
                    getLogger().warning("Error inserting player data into database: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            getLogger().warning("Error loading player data from database: " + e.getMessage());
        }
    }

    boolean isAutoTreeChopEnabled() {
        return autoTreeChopEnabled;
    }

    void setAutoTreeChopEnabled(boolean enabled) {
        this.autoTreeChopEnabled = enabled;
        updateConfig();
    }

    int getDailyUses() {
        checkAndUpdateDate();
        return dailyUses;
    }

    void incrementDailyUses() {
        checkAndUpdateDate();
        dailyUses++;
        updateConfig();
    }

    int getDailyUsesLimit() {
        return dailyUsesLimit;
    }

    void setDailyUsesLimit(int dailyUsesLimit) {
        this.dailyUsesLimit = dailyUsesLimit;
        updateConfig();
    }

    int getDailyBlocksBroken() {
        checkAndUpdateDate();
        return dailyBlocksBroken;
    }

    void incrementDailyBlocksBroken() {
        checkAndUpdateDate();
        dailyBlocksBroken++;
        updateConfig();
    }

    int getDailyBlocksBrokenLimit() {
        return dailyBlocksBrokenLimit;
    }

    void setDailyBlocksBrokenLimit(int dailyBlocksBrokenLimit) {
        this.dailyBlocksBrokenLimit = dailyBlocksBrokenLimit;
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
                "UPDATE player_data SET autoTreeChopEnabled = ?, dailyUses = ?, dailyBlocksBroken = ?, lastUseDate = ?, dailyUsesLimit = ?, dailyBlocksBrokenLimit = ? WHERE uuid = ?")) {
            statement.setBoolean(1, autoTreeChopEnabled);
            statement.setInt(2, dailyUses);
            statement.setInt(3, dailyBlocksBroken);
            statement.setString(4, lastUseDate.toString());
            statement.setInt(5, dailyUsesLimit);
            statement.setInt(6, dailyBlocksBrokenLimit);
            statement.setString(7, playerUUID.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            getLogger().warning("Error updating player data in database: " + e.getMessage());
        }
    }
}