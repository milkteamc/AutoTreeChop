package org.milkteamc.autotreechop;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import static org.bukkit.Bukkit.getLogger;

public class PlayerConfig {

    private final File configFile;
    private final FileConfiguration config;
    private boolean autoTreeChopEnabled;
    private int dailyUses;
    private int dailyBlocksBroken;
    private LocalDate lastUseDate;

    public PlayerConfig(UUID playerUUID, File dataFolder) {
        this.configFile = new File(dataFolder + "/cache", playerUUID.toString() + ".yml");
        this.config = YamlConfiguration.loadConfiguration(configFile);
        this.autoTreeChopEnabled = false;
        this.dailyUses = 0;
        this.dailyBlocksBroken = 0;
        this.lastUseDate = LocalDate.now();
        loadConfig();
        saveConfig();
    }

    private void loadConfig() {
        if (configFile.exists()) {
            autoTreeChopEnabled = config.getBoolean("autoTreeChopEnabled");
            dailyUses = config.getInt("dailyUses");
            dailyBlocksBroken = config.getInt("dailyBlocksBroken", 0);
            lastUseDate = LocalDate.parse(Objects.requireNonNull(config.getString("lastUseDate")));
        } else {
            config.set("autoTreeChopEnabled", autoTreeChopEnabled);
            config.set("dailyUses", dailyUses);
            config.set("dailyBlocksBroken", dailyBlocksBroken);
            String lastUseDateString = config.getString("lastUseDate");
            if (lastUseDateString != null) {
                lastUseDate = LocalDate.parse(lastUseDateString);
            } else {
                lastUseDate = LocalDate.now();
                config.set("lastUseDate", lastUseDate.toString());
                saveConfig();
            }
            saveConfig();
        }
    }

    private void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().warning("An error occurred:" + e);
        }
    }

    public boolean isAutoTreeChopEnabled() {
        return autoTreeChopEnabled;
    }

    public void setAutoTreeChopEnabled(boolean enabled) {
        this.autoTreeChopEnabled = enabled;
        config.set("autoTreeChopEnabled", enabled);
        saveConfig();
    }

    public int getDailyUses() {
        if (!lastUseDate.equals(LocalDate.now())) {
            dailyUses = 0;
            lastUseDate = LocalDate.now();
            config.set("dailyUses", dailyUses);
            config.set("lastUseDate", lastUseDate.toString());
            saveConfig();
        }
        return dailyUses;
    }

    public void incrementDailyUses() {
        if (!lastUseDate.equals(LocalDate.now())) {
            dailyUses = 0;
            lastUseDate = LocalDate.now();
        }
        dailyUses++;
        config.set("dailyUses", dailyUses);
        saveConfig();
    }
    public int getDailyBlocksBroken() {
        if (!lastUseDate.equals(LocalDate.now())) {
            dailyBlocksBroken = 0;
            lastUseDate = LocalDate.now();
            config.set("dailyBlocksBroken", dailyBlocksBroken);
            config.set("lastUseDate", lastUseDate.toString());
            saveConfig();
        }
        return dailyBlocksBroken;
    }

    public void incrementDailyBlocksBroken() {
        if (!lastUseDate.equals(LocalDate.now())) {
            dailyBlocksBroken = 0;
            lastUseDate = LocalDate.now();
        }
        dailyBlocksBroken++;
        config.set("dailyBlocksBroken", dailyBlocksBroken);
        saveConfig();
    }

}
