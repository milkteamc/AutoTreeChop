package org.milkteamc.autotreechop;

import com.jeff_media.updatechecker.UpdateCheckSource;
import com.jeff_media.updatechecker.UpdateChecker;
import com.jeff_media.updatechecker.UserAgentBuilder;
import de.cubbossa.tinytranslations.*;
import de.cubbossa.tinytranslations.libs.kyori.adventure.text.ComponentLike;
import de.cubbossa.tinytranslations.storage.properties.PropertiesMessageStorage;
import de.cubbossa.tinytranslations.storage.properties.PropertiesStyleStorage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.milkteamc.autotreechop.database.DatabaseManager;
import org.milkteamc.autotreechop.events.*;
import org.milkteamc.autotreechop.hooks.*;
import org.milkteamc.autotreechop.tasks.PlayerDataSaveTask;
import org.milkteamc.autotreechop.utils.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AutoTreeChop extends JavaPlugin implements CommandExecutor {

    public static final Message noResidencePermissions = new MessageBuilder("noResidencePermissions")
            .withDefault("<prefix_negative>You don't have permission to use AutoTreeChop here.</prefix_negative>").build();
    public static final Message ENABLED_MESSAGE = new MessageBuilder("enabled")
            .withDefault("<prefix>Auto tree chopping enabled.</prefix>").build();
    public static final Message DISABLED_MESSAGE = new MessageBuilder("disabled")
            .withDefault("<prefix_negative>Auto tree chopping disabled.</prefix_negative>").build();
    public static final Message NO_PERMISSION_MESSAGE = new MessageBuilder("no-permission")
            .withDefault(GlobalMessages.NO_PERM_CMD).build();
    public static final Message ONLY_PLAYERS_MESSAGE = new MessageBuilder("only-players")
            .withDefault(GlobalMessages.CMD_PLAYER_ONLY).build();
    public static final Message HIT_MAX_USAGE_MESSAGE = new MessageBuilder("hitmaxusage")
            .withDefault("<prefix_negative>You've reached the daily usage limit.</prefix_negative>").build();
    public static final Message HIT_MAX_BLOCK_MESSAGE = new MessageBuilder("hitmaxblock")
            .withDefault("<prefix_negative>You have reached your daily block breaking limit.</prefix_negative>").build();
    public static final Message USAGE_MESSAGE = new MessageBuilder("usage")
            .withDefault("<prefix>You have used the AutoTreeChop {current_uses}/{max_uses} times today.</prefix>").build();
    public static final Message BLOCKS_BROKEN_MESSAGE = new MessageBuilder("blocks-broken")
            .withDefault("<prefix>You have broken {current_blocks}/{max_blocks} blocks today.</prefix>").build();
    public static final Message ENABLED_BY_OTHER_MESSAGE = new MessageBuilder("enabledByOther")
            .withDefault("<prefix>Auto tree chopping enabled by {player}.</prefix>").build();
    public static final Message ENABLED_FOR_OTHER_MESSAGE = new MessageBuilder("enabledForOther")
            .withDefault("<prefix>Auto tree chopping enabled for {player}</prefix>").build();
    public static final Message DISABLED_BY_OTHER_MESSAGE = new MessageBuilder("disabledByOther")
            .withDefault("<prefix_negative>Auto tree chopping disabled by {player}.</prefix_negative>").build();
    public static final Message DISABLED_FOR_OTHER_MESSAGE = new MessageBuilder("disabledForOther")
            .withDefault("<prefix_negative>Auto tree chopping disabled for {player}</prefix_negative>").build();
    public static final Message STILL_IN_COOLDOWN_MESSAGE = new MessageBuilder("stillInCooldown")
            .withDefault("<prefix_negative>You are still in cooldown! Try again after {cooldown_time} seconds.</prefix_negative>").build();
    public static final Message CONSOLE_NAME = new MessageBuilder("consoleName")
            .withDefault("console").build();
    public static final Message SNEAK_ENABLED_MESSAGE = new MessageBuilder("sneakEnabled")
            .withDefault("<prefix>Auto tree chopping enabled by sneaking.</prefix>").build();
    public static final Message SNEAK_DISABLED_MESSAGE = new MessageBuilder("sneakDisabled")
            .withDefault("<prefix_negative>Auto tree chopping disabled by stopping sneak.</prefix_negative>").build();

    private static final String SPIGOT_RESOURCE_ID = "113071";
    private static final List<String> SUPPORTED_VERSIONS = Arrays.asList(
            "1.21.10", "1.21.9", "1.21.8", "1.21.7", "1.21.6", "1.21.5", "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
            "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
            "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
            "1.18.2", "1.18.1", "1.18",
            "1.17.1", "1.17"
    );

    private static final long SAVE_INTERVAL = 1200L; // 60s
    private static final int SAVE_THRESHOLD = 15;

    private Config config;
    private AutoTreeChopAPI autoTreeChopAPI;
    private Map<UUID, PlayerConfig> playerConfigs = new ConcurrentHashMap<>();
    private String bukkitVersion = this.getServer().getBukkitVersion();
    private Metrics metrics;
    private MessageTranslator translations;

    private boolean worldGuardEnabled = false;
    private boolean residenceEnabled = false;
    private boolean griefPreventionEnabled = false;
    private boolean landsEnabled = false;
    private WorldGuardHook worldGuardHook = null;
    private ResidenceHook residenceHook = null;
    private GriefPreventionHook griefPreventionHook = null;
    private LandsHook landsHook = null;

    private CooldownManager cooldownManager;

    private DatabaseManager databaseManager;
    private PlayerDataSaveTask saveTask;

    private TreeChopUtils treeChopUtils;

    public static void sendMessage(CommandSender sender, ComponentLike message) {
        BukkitTinyTranslations.sendMessageIfNotEmpty(sender, message);
    }

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new Config(this);

        if (bukkitVersion.length() > 14) {
            bukkitVersion = bukkitVersion.substring(0, bukkitVersion.length() - 14);
            if (!SUPPORTED_VERSIONS.contains(bukkitVersion)) {
                getLogger().warning("Your Minecraft version didn't fully tested yet.");
                getLogger().warning("IF you have any issues, feel free to report it at our GitHub: https://github.com/milkteamc/AutoTreeChop/issues");
            }
        }

        metrics = new Metrics(this, 20053);

        // Register event listeners
        registerEvents();

        // Register command and tab completer
        org.milkteamc.autotreechop.command.Command command = new org.milkteamc.autotreechop.command.Command(this);
        Objects.requireNonNull(getCommand("autotreechop")).setExecutor(command);
        Objects.requireNonNull(getCommand("atc")).setExecutor(command);
        Objects.requireNonNull(getCommand("autotreechop")).setTabCompleter(new org.milkteamc.autotreechop.command.TabCompleter());
        Objects.requireNonNull(getCommand("atc")).setTabCompleter(new org.milkteamc.autotreechop.command.TabCompleter());

        translations = BukkitTinyTranslations.application(this);
        translations.setMessageStorage(new PropertiesMessageStorage(new File(getDataFolder(), "/lang/")));
        translations.setStyleStorage(new PropertiesStyleStorage(new File(getDataFolder(), "/lang/styles.properties")));
        translations.addMessages(TinyTranslations.messageFieldsFromClass(AutoTreeChop.class));

        loadLocale();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new AutoTreeChopExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion for AutoTreeChop has been registered.");
        } else {
            getLogger().warning("PlaceholderAPI not found. Placeholder expansion for AutoTreeChop will not work.");
        }

        new UpdateChecker(this, UpdateCheckSource.SPIGOT, SPIGOT_RESOURCE_ID)
                .checkEveryXHours(24)
                .setDonationLink("https://ko-fi.com/maoyue")
                .setChangelogLink("https://modrinth.com/plugin/autotreechop/version/latest")
                .setDownloadLink("https://modrinth.com/plugin/autotreechop/version/latest")
                .setNotifyOpsOnJoin(true)
                .setNotifyByPermissionOnJoin("autotreechop.updatechecker")
                .setUserAgent(new UserAgentBuilder().addPluginNameAndVersion())
                .checkNow();

        databaseManager = new DatabaseManager(
                this,
                config.isUseMysql(),
                config.getHostname(),
                config.getPort(),
                config.getDatabase(),
                config.getUsername(),
                config.getPassword()
        );

        saveTask = new PlayerDataSaveTask(this, SAVE_THRESHOLD);
        saveTask.runTaskTimerAsynchronously(this, SAVE_INTERVAL, SAVE_INTERVAL);

        autoTreeChopAPI = new AutoTreeChopAPI(this);
        playerConfigs = new ConcurrentHashMap<>();
        initializeHooks();

        cooldownManager = new CooldownManager(this);

        this.treeChopUtils = new TreeChopUtils(this);

        getLogger().info("AutoTreeChop enabled!");
    }

    private void registerEvents() {
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerSneakListener(this), this);
    }

    private void initializeHooks() {
        if (Bukkit.getPluginManager().getPlugin("Residence") != null) {
            try {
                residenceHook = new ResidenceHook(config.getResidenceFlag());
                residenceEnabled = true;
                getLogger().info("Residence support enabled");
            } catch (Exception e) {
                getLogger().warning("Residence can't be hook, please report this to our GitHub: https://github.com/milkteamc/AutoTreeChop/issues");
                residenceEnabled = false;
            }
        } else {
            residenceEnabled = false;
        }

        if (Bukkit.getPluginManager().getPlugin("GriefPrevention") != null) {
            try {
                griefPreventionHook = new GriefPreventionHook(config.getGriefPreventionFlag());
                griefPreventionEnabled = true;
                getLogger().info("GriefPrevention support enabled");
            } catch (Exception e) {
                getLogger().warning("GriefPrevention can't be hook, please report this to our GitHub: https://github.com/milkteamc/AutoTreeChop/issues");
                griefPreventionEnabled = false;
            }
        } else {
            griefPreventionEnabled = false;
        }

        if (Bukkit.getPluginManager().getPlugin("Lands") != null) {
            try {
                landsHook = new LandsHook(this);
                landsEnabled = true;
                getLogger().info("Lands support enabled");
            } catch (Exception e) {
                getLogger().warning("Lands can't be hook, please report this to our GitHub: https://github.com/milkteamc/AutoTreeChop/issues");
                landsEnabled = false;
            }
        } else {
            landsEnabled = false;
        }

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                worldGuardHook = new WorldGuardHook();
                worldGuardEnabled = true;
                getLogger().info("WorldGuard support enabled");
            } catch (NoClassDefFoundError e) {
                getLogger().warning("WorldGuard can't be hook, please report this to our GitHub: https://github.com/milkteamc/AutoTreeChop/issues");
                worldGuardEnabled = false;
            }
        } else {
            worldGuardEnabled = false;
        }
    }

    private void loadLocale() {
        saveResourceIfNotExists("lang/styles.properties");
        saveResourceIfNotExists("lang/de.properties");
        saveResourceIfNotExists("lang/es.properties");
        saveResourceIfNotExists("lang/fr.properties");
        saveResourceIfNotExists("lang/ja.properties");
        saveResourceIfNotExists("lang/ru.properties");
        saveResourceIfNotExists("lang/zh.properties");
        translations.setUseClientLocale(config.isUseClientLocale());
        translations.defaultLocale(config.getLocale() == null ? Locale.getDefault() : config.getLocale());
        translations.loadStyles();
        translations.loadLocales();
    }

    private void saveResourceIfNotExists(String resourcePath) {
        if (!new File(getDataFolder(), resourcePath).exists()) {
            saveResource(resourcePath, false);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Saving all player data before shutdown...");

        if (saveTask != null) {
            saveTask.cancel();
        }

        for (Map.Entry<UUID, PlayerConfig> entry : playerConfigs.entrySet()) {
            if (entry.getValue().isDirty()) {
                databaseManager.savePlayerDataSync(entry.getValue().getData());
            }
        }

        playerConfigs.clear();

        if (databaseManager != null) {
            databaseManager.close();
        }

        SessionManager sessionManager = SessionManager.getInstance();
        for (UUID uuid : new HashSet<>(playerConfigs.keySet())) {
            sessionManager.clearAllPlayerSessions(uuid);
        }

        translations.close();
        metrics.shutdown();

        getLogger().info("AutoTreeChop disabled!");
    }

    public PlayerConfig getPlayerConfig(UUID playerUUID) {
        PlayerConfig playerConfig = playerConfigs.get(playerUUID);

        if (playerConfig == null) {
            getLogger().warning("PlayerConfig not found for " + playerUUID + ", loading synchronously");
            try {
                DatabaseManager.PlayerData data = databaseManager.loadPlayerDataAsync(
                        playerUUID,
                        config.getDefaultTreeChop()
                ).get();

                playerConfig = new PlayerConfig(playerUUID, data);
                playerConfigs.put(playerUUID, playerConfig);
            } catch (Exception e) {
                getLogger().warning("Failed to load player data: " + e.getMessage());
                DatabaseManager.PlayerData defaultData = new DatabaseManager.PlayerData(
                        playerUUID,
                        config.getDefaultTreeChop(),
                        0,
                        0,
                        java.time.LocalDate.now()
                );
                playerConfig = new PlayerConfig(playerUUID, defaultData);
                playerConfigs.put(playerUUID, playerConfig);
            }
        }

        return playerConfig;
    }

    public int getPlayerDailyUses(UUID playerUUID) {
        return getPlayerConfig(playerUUID).getDailyUses();
    }

    public int getPlayerDailyBlocksBroken(UUID playerUUID) {
        return getPlayerConfig(playerUUID).getDailyBlocksBroken();
    }

    public AutoTreeChopAPI getAutoTreeChopAPI() {
        return autoTreeChopAPI;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public Config getPluginConfig() {
        return config;
    }

    public Map<UUID, PlayerConfig> getAllPlayerConfigs() {
        return playerConfigs;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public TreeChopUtils getTreeChopUtils() {
        return treeChopUtils;
    }

    public boolean isWorldGuardEnabled() {
        return worldGuardEnabled;
    }

    public boolean isResidenceEnabled() {
        return residenceEnabled;
    }

    public boolean isGriefPreventionEnabled() {
        return griefPreventionEnabled;
    }

    public boolean isLandsEnabled() {
        return landsEnabled;
    }

    public WorldGuardHook getWorldGuardHook() {
        return worldGuardHook;
    }

    public ResidenceHook getResidenceHook() {
        return residenceHook;
    }

    public GriefPreventionHook getGriefPreventionHook() {
        return griefPreventionHook;
    }

    public LandsHook getLandsHook() {
        return landsHook;
    }
}