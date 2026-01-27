package org.milkteamc.autotreechop;

import com.cryptomorin.xseries.XMaterial;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;

public class Config {

    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String CONFIG_VERSION_KEY = "config-version";

    private final AutoTreeChop plugin;
    private YamlDocument config;

    private boolean visualEffect;
    private boolean toolDamage;
    private int maxUsesPerDay;
    private int maxBlocksPerDay;
    private int cooldownTime;
    private int vipCooldownTime;
    private boolean stopChoppingIfNotConnected;
    private boolean stopChoppingIfDifferentTypes;
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

        try {
            config = YamlDocument.create(
                    configFile,
                    plugin.getResource("config.yml"),
                    GeneralSettings.DEFAULT,
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder()
                            .setVersioning(new BasicVersioning(CONFIG_VERSION_KEY))
                            .build());

            if (config.getBoolean("__updated__", false)) {
                backupConfig(configFile);
                config.set("__updated__", null);
                config.save();
            }

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load config.yml: " + e.getMessage());

            if (configFile.exists()) {
                backupConfig(configFile);
            }

            try {
                plugin.saveResource("config.yml", true);
                config = YamlDocument.create(
                        configFile,
                        plugin.getResource("config.yml"),
                        GeneralSettings.DEFAULT,
                        LoaderSettings.DEFAULT,
                        DumperSettings.DEFAULT,
                        UpdaterSettings.builder()
                                .setVersioning(new BasicVersioning(CONFIG_VERSION_KEY))
                                .build());
                plugin.getLogger().info("Created new config.yml from defaults");
            } catch (IOException ex) {
                plugin.getLogger().severe("Failed to create default config: " + ex.getMessage());
                throw new RuntimeException("Cannot initialize config", ex);
            }
        }

        loadValues();
    }

    private void loadValues() {
        visualEffect = config.getBoolean("visual-effect", true);
        toolDamage = config.getBoolean("toolDamage", true);
        maxUsesPerDay = config.getInt("max-uses-per-day", 50);
        maxBlocksPerDay = config.getInt("max-blocks-per-day", 500);
        cooldownTime = config.getInt("cooldownTime", 5);
        vipCooldownTime = config.getInt("vipCooldownTime", 2);
        stopChoppingIfNotConnected = config.getBoolean("stopChoppingIfNotConnected", false);
        stopChoppingIfDifferentTypes = config.getBoolean("stopChoppingIfDifferentTypes", false);
        residenceFlag = config.getString("residenceFlag", "build");
        griefPreventionFlag = config.getString("griefPreventionFlag", "Build");
        useClientLocale = config.getBoolean("use-player-locale", false);

        useMysql = config.getBoolean("useMysql", false);
        hostname = config.getString("hostname", "example.com");
        port = config.getInt("port", 3306);
        database = config.getString("database", "example");
        username = config.getString("username", "root");
        password = config.getString("password", "abc1234");

        limitVipUsage = config.getBoolean("limitVipUsage", false);
        vipUsesPerDay = config.getInt("vip-uses-per-day", 100);
        vipBlocksPerDay = config.getInt("vip-blocks-per-day", 1000);

        toolDamageDecrease = config.getInt("toolDamageDecrease", 1);
        mustUseTool = config.getBoolean("mustUseTool", false);
        respectUnbreaking = config.getBoolean("respectUnbreaking", true);

        defaultTreeChop = config.getBoolean("defaultTreeChop", false);
        playBreakSound = config.getBoolean("playBreakSound", true);
        sneakToggle = config.getBoolean("enable-sneak-toggle", false);
        commandToggle = config.getBoolean("enable-command-toggle", true);
        sneakMessage = config.getBoolean("sneak-message", false);

        chopBatchSize = config.getInt("chop-batch-size", 50);
        maxTreeSize = config.getInt("max-tree-size", 500);
        maxDiscoveryBlocks = config.getInt("max-discovery-blocks", 1000);
        callBlockBreakEvent = config.getBoolean("call-block-break-event", true);

        autoReplantEnabled = config.getBoolean("enable-auto-replant", true);
        replantDelayTicks = config.getLong("replant-delay-ticks", 15L);
        requireSaplingInInventory = config.getBoolean("require-sapling-in-inventory", false);
        replantVisualEffect = config.getBoolean("replant-visual-effect", true);

        leafRemovalEnabled = config.getBoolean("enable-leaf-removal", true);
        leafRemovalDelayTicks = config.getLong("leaf-removal-delay-ticks", 5L);
        leafRemovalRadius = config.getInt("leaf-removal-radius", 10);
        leafRemovalDropItems = config.getBoolean("leaf-removal-drop-items", false);
        leafRemovalVisualEffects = config.getBoolean("leaf-removal-visual-effects", true);
        leafRemovalAsync = config.getBoolean("leaf-removal-async", true);
        leafRemovalBatchSize = config.getInt("leaf-removal-batch-size", 20);
        leafRemovalCountsTowardsLimit = config.getBoolean("leaf-removal-counts-towards-limit", false);
        leafRemovalMode = config.getString("leaf-removal-mode", "smart");

        String localeStr = config.getString("locale", "en");
        try {
            this.locale = Locale.forLanguageTag(localeStr.replace('_', '-'));
        } catch (Exception e) {
            this.locale = Locale.ENGLISH;
            plugin.getLogger().warning("Invalid locale '" + localeStr + "' in config.yml. Using default: English");
        }

        logTypes = loadMaterialSet("log-types");
        leafTypes = loadMaterialSet("leaf-types");
        validSoilTypes = loadMaterialSet("valid-soil-types");

        logSaplingMapping = loadLogSaplingMapping();

        plugin.getLogger().info("Loaded " + logTypes.size() + " log types");
        plugin.getLogger().info("Loaded " + leafTypes.size() + " leaf types");
        plugin.getLogger().info("Loaded " + logSaplingMapping.size() + " log-sapling mappings");
    }

    private Set<Material> loadMaterialSet(String path) {
        List<String> materialNames = config.getStringList(path);
        return materialNames.stream()
                .map(this::parseMaterial)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Material parseMaterial(String name) {
        Optional<XMaterial> xMat = XMaterial.matchXMaterial(name);
        if (xMat.isPresent()) {
            Material mat = xMat.get().get();
            if (mat != null) {
                return mat;
            }
        }

        try {
            return Material.getMaterial(name);
        } catch (Exception e) {
            plugin.getLogger().fine("Material not available in this version: " + name);
            return null;
        }
    }

    private Map<Material, Material> loadLogSaplingMapping() {
        Map<Material, Material> mapping = new HashMap<>();

        var section = config.getSection("log-sapling-mapping");
        if (section == null) {
            plugin.getLogger().warning("log-sapling-mapping section not found in config");
            return mapping;
        }

        Set<Object> keys = section.getKeys();
        for (Object keyObj : keys) {
            String logTypeStr = keyObj.toString();
            String saplingTypeStr = config.getString("log-sapling-mapping." + logTypeStr);

            if (saplingTypeStr == null) {
                continue;
            }

            Material logType = parseMaterial(logTypeStr);
            Material saplingType = parseMaterial(saplingTypeStr);

            if (logType != null && saplingType != null) {
                mapping.put(logType, saplingType);
            } else {
                plugin.getLogger()
                        .fine("Skipping log-sapling mapping (materials not available): " + logTypeStr + " -> "
                                + saplingTypeStr);
            }
        }

        return mapping;
    }

    private void backupConfig(File configFile) {
        if (!configFile.exists()) {
            return;
        }

        try {
            String timestamp = LocalDateTime.now().format(BACKUP_FORMAT);
            File backupFile = new File(plugin.getDataFolder(), "config.yml.backup." + timestamp);
            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Config backed up to: " + backupFile.getName());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to backup config: " + e.getMessage());
        }
    }

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
