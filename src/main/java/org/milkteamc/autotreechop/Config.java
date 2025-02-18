package org.milkteamc.autotreechop;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Config {

    private final AutoTreeChop plugin;

    // Configuration variables
    private boolean visualEffect;
    private boolean toolDamage;
    private int maxUsesPerDay;
    private int maxBlocksPerDay;
    private int cooldownTime;
    private int vipCooldownTime;
    private boolean stopChoppingIfNotConnected;
    private boolean stopChoppingIfDifferentTypes;
    private boolean chopTreeAsync;
    private String residenceFlag;
    private String griefPreventionFlag;
    private Locale locale;
    private boolean useClientLocale;
    private boolean useMysql;
    private String hostname;
    private int port;
    private String database;
    private String username;
    private String password;
    private boolean limitVipUsage;
    private int vipUsesPerDay;
    private int vipBlocksPerDay;
    private int toolDamageDecrease;
    private Set<Material> logTypes;


    public Config(AutoTreeChop plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        FileConfiguration defaultConfig = getDefaultConfig();

        if (!configFile.exists()) {
            try {
                if (!configFile.getParentFile().exists()) {
                    configFile.getParentFile().mkdirs();
                }
                configFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("An error occurred:" + e);
                return;
            }
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Merge with default config (VERY IMPORTANT for updates)
        for (String key : defaultConfig.getKeys(true)) {
            if (!config.contains(key)) {
                config.set(key, defaultConfig.get(key));
            }
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("An error occurred while saving config: " + e);
        }


        // Load values from config
        visualEffect = config.getBoolean("visual-effect");
        toolDamage = config.getBoolean("toolDamage");
        maxUsesPerDay = config.getInt("max-uses-per-day");
        maxBlocksPerDay = config.getInt("max-blocks-per-day");
        stopChoppingIfNotConnected = config.getBoolean("stopChoppingIfNotConnected");
        stopChoppingIfDifferentTypes = config.getBoolean("stopChoppingIfDifferentTypes");
        chopTreeAsync = config.getBoolean("chopTreeAsync");
        residenceFlag = config.getString("residenceFlag");
        griefPreventionFlag = config.getString("griefPreventionFlag");
        cooldownTime = config.getInt("cooldownTime");
        vipCooldownTime = config.getInt("vipCooldownTime");
        useClientLocale = config.getBoolean("use-player-locale");
        useMysql = config.getBoolean("useMysql");
        hostname = config.getString("hostname");
        port = config.getInt("port");
        database = config.getString("database");
        username = config.getString("username");
        password = config.getString("password");
        limitVipUsage = config.getBoolean("limitVipUsage");
        vipUsesPerDay = config.getInt("vip-uses-per-day");
        vipBlocksPerDay = config.getInt("vip-blocks-per-day");
        toolDamageDecrease = config.getInt("toolDamageDecrease");

        // Load log types
        List<String> logTypeStrings = config.getStringList("log-types");
        logTypes = logTypeStrings.stream()
                .map(Material::getMaterial)
                .filter(Objects::nonNull)  // Filter out null materials (invalid names)
                .collect(Collectors.toSet());

        //Locale handling\
        Object localeObj = config.get("locale");
        if (localeObj instanceof String) {
            this.locale = Locale.forLanguageTag((String) localeObj);
        } else if (localeObj instanceof Locale) {
            this.locale = (Locale) localeObj;
        } else {
            this.locale = Locale.ENGLISH; // Default to English if invalid
            plugin.getLogger().warning("Invalid locale setting in config.yml.  Using default: English");
        }
    }


    private FileConfiguration getDefaultConfig() {
        FileConfiguration defaultConfig = new YamlConfiguration();
        defaultConfig.set("visual-effect", true);
        defaultConfig.set("toolDamage", true);
        defaultConfig.set("max-uses-per-day", 50);
        defaultConfig.set("max-blocks-per-day", 500);
        defaultConfig.set("cooldownTime", 5);
        defaultConfig.set("vipCooldownTime", 2);
        defaultConfig.set("stopChoppingIfNotConnected", false);
        defaultConfig.set("stopChoppingIfDifferentTypes", false);
        defaultConfig.set("chopTreeAsync", true);
        defaultConfig.set("use-player-locale", false);
        defaultConfig.set("useMysql", false);
        defaultConfig.set("hostname", "example.com");
        defaultConfig.set("port", 3306);
        defaultConfig.set("database", "example");
        defaultConfig.set("username", "root");
        defaultConfig.set("password", "abc1234");
        defaultConfig.set("locale", Locale.ENGLISH.toString()); // Store as string for consistency
        defaultConfig.set("residenceFlag", "build");
        defaultConfig.set("griefPreventionFlag", "Build");
        defaultConfig.set("limitVipUsage", true);
        defaultConfig.set("vip-uses-per-day", 50);
        defaultConfig.set("vip-blocks-per-day", 500);
        defaultConfig.set("toolDamageDecrease", 1);
        defaultConfig.set("log-types", Arrays.asList("OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "JUNGLE_LOG", "ACACIA_LOG", "DARK_OAK_LOG", "MANGROVE_LOG", "CHERRY_LOG"));
        return defaultConfig;
    }


    // Getters for all config options
    public boolean isVisualEffect() {
        return visualEffect;
    }

    public boolean isToolDamage() {
        return toolDamage;
    }

    public int getMaxUsesPerDay() {
        return maxUsesPerDay;
    }

    public int getMaxBlocksPerDay() {
        return maxBlocksPerDay;
    }

    public int getCooldownTime() {
        return cooldownTime;
    }

    public int getVipCooldownTime() {
        return vipCooldownTime;
    }

    public boolean isStopChoppingIfNotConnected() {
        return stopChoppingIfNotConnected;
    }

    public boolean isStopChoppingIfDifferentTypes() {
        return stopChoppingIfDifferentTypes;
    }

    public boolean isChopTreeAsync() {
        return chopTreeAsync;
    }

    public String getResidenceFlag() {
        return residenceFlag;
    }

    public String getGriefPreventionFlag() {
        return griefPreventionFlag;
    }

    public Locale getLocale() {
        return locale;
    }

    public boolean isUseClientLocale() {
        return useClientLocale;
    }

    public boolean isUseMysql() {
        return useMysql;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean getLimitVipUsage() {
        return limitVipUsage;
    }

    public int getVipUsesPerDay() {
        return vipUsesPerDay;
    }

    public int getVipBlocksPerDay() {
        return vipBlocksPerDay;
    }

    public int getToolDamageDecrease() {
        return toolDamageDecrease;
    }

    public Set<Material> getLogTypes() {
        return logTypes;
    }
}
