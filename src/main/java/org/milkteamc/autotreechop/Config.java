package org.milkteamc.autotreechop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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
    private boolean mustUseTool;
    private boolean defaultTreeChop;
    private boolean respectUnbreaking;
    private boolean playBreakSound;
    private Set<Material> logTypes;
    private boolean sneakToggle;
    private boolean commandToggle;
    private boolean sneakMessage;
    private boolean autoReplantEnabled;
    private long replantDelayTicks;
    private boolean requireSaplingInInventory;
    private boolean replantVisualEffect;
    private Map<Material, Material> logSaplingMapping;
    private Set<Material> validSoilTypes;
    private boolean leafRemovalEnabled;
    private long leafRemovalDelayTicks;
    private int leafRemovalRadius;
    private boolean leafRemovalDropItems;
    private boolean leafRemovalVisualEffects;
    private boolean leafRemovalAsync;
    private int leafRemovalBatchSize;
    private boolean leafRemovalCountsTowardsLimit;
    private String leafRemovalMode;
    private Set<Material> leafTypes;

    private int chopBatchSize;
    private int maxTreeSize;
    private int maxDiscoveryBlocks;
    private boolean callBlockBreakEvent;

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
        mustUseTool = config.getBoolean("mustUseTool");
        defaultTreeChop = config.getBoolean("defaultTreeChop");
        respectUnbreaking = config.getBoolean("respectUnbreaking");
        playBreakSound = config.getBoolean("playBreakSound");
        sneakToggle = config.getBoolean("enable-sneak-toggle");
        commandToggle = config.getBoolean("enable-command-toggle");
        sneakMessage = config.getBoolean("sneak-message");
        autoReplantEnabled = config.getBoolean("enable-auto-replant");
        replantDelayTicks = config.getLong("replant-delay-ticks");
        requireSaplingInInventory = config.getBoolean("require-sapling-in-inventory");
        replantVisualEffect = config.getBoolean("replant-visual-effect");
        leafRemovalEnabled = config.getBoolean("enable-leaf-removal");
        leafRemovalDelayTicks = config.getLong("leaf-removal-delay-ticks");
        leafRemovalRadius = config.getInt("leaf-removal-radius");
        leafRemovalDropItems = config.getBoolean("leaf-removal-drop-items");
        leafRemovalVisualEffects = config.getBoolean("leaf-removal-visual-effects");
        leafRemovalAsync = config.getBoolean("leaf-removal-async");
        leafRemovalBatchSize = config.getInt("leaf-removal-batch-size");
        leafRemovalCountsTowardsLimit = config.getBoolean("leaf-removal-counts-towards-limit");
        leafRemovalMode = config.getString("leaf-removal-mode", "smart");

        chopBatchSize = config.getInt("chop-batch-size", 50);
        maxTreeSize = config.getInt("max-tree-size", 500);
        maxDiscoveryBlocks = config.getInt("max-discovery-blocks", 1000);
        callBlockBreakEvent = config.getBoolean("call-block-break-event", true);

        // Load leaf types
        List<String> leafTypeStrings = config.getStringList("leaf-types");
        leafTypes = leafTypeStrings.stream()
                .map(Material::getMaterial)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<String> soilTypeStrings = config.getStringList("valid-soil-types");
        validSoilTypes = soilTypeStrings.stream()
                .map(Material::getMaterial)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        logSaplingMapping = new HashMap<>();
        ConfigurationSection mappingSection = config.getConfigurationSection("log-sapling-mapping");
        if (mappingSection != null) {
            for (String logTypeStr : mappingSection.getKeys(false)) {
                Material logType = Material.getMaterial(logTypeStr);
                Material saplingType = Material.getMaterial(mappingSection.getString(logTypeStr));
                if (logType != null && saplingType != null) {
                    logSaplingMapping.put(logType, saplingType);
                }
            }
        }

        // Load log types
        List<String> logTypeStrings = config.getStringList("log-types");
        logTypes = logTypeStrings.stream()
                .map(Material::getMaterial)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Locale handling
        Object localeObj = config.get("locale");
        if (localeObj instanceof String) {
            this.locale = Locale.forLanguageTag((String) localeObj);
        } else if (localeObj instanceof Locale) {
            this.locale = (Locale) localeObj;
        } else {
            this.locale = Locale.ENGLISH;
            plugin.getLogger().warning("Invalid locale setting in config.yml. Using default: English");
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
        defaultConfig.set("locale", Locale.ENGLISH.toString());
        defaultConfig.set("residenceFlag", "build");
        defaultConfig.set("griefPreventionFlag", "Build");
        defaultConfig.set("limitVipUsage", true);
        defaultConfig.set("vip-uses-per-day", 50);
        defaultConfig.set("vip-blocks-per-day", 500);
        defaultConfig.set("toolDamageDecrease", 1);
        defaultConfig.set("mustUseTool", false);
        defaultConfig.set("defaultTreeChop", false);
        defaultConfig.set("respectUnbreaking", true);
        defaultConfig.set("playBreakSound", true);
        defaultConfig.set("enable-sneak-toggle", false);
        defaultConfig.set("enable-command-toggle", true);
        defaultConfig.set("sneak-message", false);
        defaultConfig.set("log-types", Arrays.asList("OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "JUNGLE_LOG", "ACACIA_LOG", "DARK_OAK_LOG", "MANGROVE_LOG", "CHERRY_LOG"));
        defaultConfig.set("enable-auto-replant", true);
        defaultConfig.set("replant-delay-ticks", 1L);
        defaultConfig.set("require-sapling-in-inventory", false);
        defaultConfig.set("replant-visual-effect", true);
        defaultConfig.set("valid-soil-types", Arrays.asList(
                "DIRT", "GRASS_BLOCK", "PODZOL", "COARSE_DIRT", "ROOTED_DIRT"
        ));
        ConfigurationSection logSaplingSection = defaultConfig.createSection("log-sapling-mapping");
        logSaplingSection.set("OAK_LOG", "OAK_SAPLING");
        logSaplingSection.set("BIRCH_LOG", "BIRCH_SAPLING");
        logSaplingSection.set("SPRUCE_LOG", "SPRUCE_SAPLING");
        logSaplingSection.set("JUNGLE_LOG", "JUNGLE_SAPLING");
        logSaplingSection.set("ACACIA_LOG", "ACACIA_SAPLING");
        logSaplingSection.set("DARK_OAK_LOG", "DARK_OAK_SAPLING");
        logSaplingSection.set("MANGROVE_LOG", "MANGROVE_PROPAGULE");
        logSaplingSection.set("CHERRY_LOG", "CHERRY_SAPLING");
        logSaplingSection.set("PALE_OAK_LOG", "PALE_OAK_SAPLING");

        defaultConfig.set("enable-leaf-removal", true);
        defaultConfig.set("leaf-removal-delay-ticks", 1L);
        defaultConfig.set("leaf-removal-radius", 8);
        defaultConfig.set("leaf-removal-drop-items", false);
        defaultConfig.set("leaf-removal-visual-effects", true);
        defaultConfig.set("leaf-removal-async", true);
        defaultConfig.set("leaf-removal-batch-size", 20);
        defaultConfig.set("leaf-removal-counts-towards-limit", false);
        defaultConfig.set("leaf-types", Arrays.asList("OAK_LEAVES", "SPRUCE_LEAVES", "BIRCH_LEAVES", "JUNGLE_LEAVES",
                "ACACIA_LEAVES", "DARK_OAK_LEAVES", "MANGROVE_LEAVES", "CHERRY_LEAVES", "PALE_OAK_LEAVES"));
        defaultConfig.set("leaf-removal-mode", "smart");

        defaultConfig.set("chop-batch-size", 50);
        defaultConfig.set("max-tree-size", 500);
        defaultConfig.set("max-discovery-blocks", 1000);
        defaultConfig.set("call-block-break-event", true);

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

    public boolean getMustUseTool() {
        return mustUseTool;
    }

    public boolean getDefaultTreeChop() {
        return defaultTreeChop;
    }

    public boolean getRespectUnbreaking() {
        return respectUnbreaking;
    }

    public boolean getPlayBreakSound() {
        return playBreakSound;
    }

    public boolean getSneakToggle() {
        return sneakToggle;
    }

    public boolean getCommandToggle() {
        return commandToggle;
    }

    public boolean getSneakMessage() {
        return sneakMessage;
    }

    public Set<Material> getLogTypes() {
        return logTypes;
    }

    public boolean isAutoReplantEnabled() {
        return autoReplantEnabled;
    }

    public long getReplantDelayTicks() {
        return replantDelayTicks;
    }

    public boolean getRequireSaplingInInventory() {
        return requireSaplingInInventory;
    }

    public boolean getReplantVisualEffect() {
        return replantVisualEffect;
    }

    public Set<Material> getValidSoilTypes() {
        return validSoilTypes;
    }

    public Map<Material, Material> getLogSaplingMapping() {
        return logSaplingMapping;
    }

    public Material getSaplingForLog(Material logType) {
        return logSaplingMapping.get(logType);
    }

    public boolean isLeafRemovalEnabled() {
        return leafRemovalEnabled;
    }

    public long getLeafRemovalDelayTicks() {
        return leafRemovalDelayTicks;
    }

    public int getLeafRemovalRadius() {
        return leafRemovalRadius;
    }

    public boolean getLeafRemovalDropItems() {
        return leafRemovalDropItems;
    }

    public boolean getLeafRemovalVisualEffects() {
        return leafRemovalVisualEffects;
    }

    public boolean isLeafRemovalAsync() {
        return leafRemovalAsync;
    }

    public int getLeafRemovalBatchSize() {
        return leafRemovalBatchSize;
    }

    public boolean getLeafRemovalCountsTowardsLimit() {
        return leafRemovalCountsTowardsLimit;
    }

    public Set<Material> getLeafTypes() {
        return leafTypes;
    }

    public String getLeafRemovalMode() {
        return leafRemovalMode;
    }

    public int getChopBatchSize() {
        return chopBatchSize;
    }

    public int getMaxTreeSize() {
        return maxTreeSize;
    }

    public int getMaxDiscoveryBlocks() {
        return maxDiscoveryBlocks;
    }

    public boolean isCallBlockBreakEvent() {
        return callBlockBreakEvent;
    }
}