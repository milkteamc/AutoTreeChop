package org.milkteamc.autotreechop;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.milkteamc.autotreechop.command.AboutCommand;
import org.milkteamc.autotreechop.command.ConfirmCommand;
import org.milkteamc.autotreechop.command.MainCommand;
import org.milkteamc.autotreechop.command.ReloadCommand;
import org.milkteamc.autotreechop.command.ToggleCommand;
import org.milkteamc.autotreechop.command.UsageCommand;
import org.milkteamc.autotreechop.database.DatabaseManager;
import org.milkteamc.autotreechop.events.BlockBreakListener;
import org.milkteamc.autotreechop.events.PlayerJoinListener;
import org.milkteamc.autotreechop.events.PlayerQuitListener;
import org.milkteamc.autotreechop.events.PlayerSneakListener;
import org.milkteamc.autotreechop.hooks.GriefPreventionHook;
import org.milkteamc.autotreechop.hooks.LandsHook;
import org.milkteamc.autotreechop.hooks.ResidenceHook;
import org.milkteamc.autotreechop.hooks.WorldGuardHook;
import org.milkteamc.autotreechop.tasks.PlayerDataSaveTask;
import org.milkteamc.autotreechop.translation.TranslationManager;
import org.milkteamc.autotreechop.utils.ConfirmationManager;
import org.milkteamc.autotreechop.utils.CooldownManager;
import org.milkteamc.autotreechop.utils.SessionManager;
import org.milkteamc.autotreechop.utils.TreeChopUtils;
import revxrsal.commands.bukkit.BukkitLamp;

public class AutoTreeChop extends JavaPlugin {

    // Message keys (replacing old Message objects)
    public static final String NO_RESIDENCE_PERMISSIONS = "noResidencePermissions";
    public static final String ENABLED_MESSAGE = "enabled";
    public static final String DISABLED_MESSAGE = "disabled";
    public static final String NO_PERMISSION_MESSAGE = "no-permission";
    public static final String ONLY_PLAYERS_MESSAGE = "only-players";
    public static final String HIT_MAX_USAGE_MESSAGE = "hitmaxusage";
    public static final String HIT_MAX_BLOCK_MESSAGE = "hitmaxblock";
    public static final String USAGE_MESSAGE = "usage";
    public static final String BLOCKS_BROKEN_MESSAGE = "blocks-broken";
    public static final String ENABLED_BY_OTHER_MESSAGE = "enabledByOther";
    public static final String ENABLED_FOR_OTHER_MESSAGE = "enabledForOther";
    public static final String DISABLED_BY_OTHER_MESSAGE = "disabledByOther";
    public static final String DISABLED_FOR_OTHER_MESSAGE = "disabledForOther";
    public static final String STILL_IN_COOLDOWN_MESSAGE = "stillInCooldown";
    public static final String CONSOLE_NAME = "consoleName";
    public static final String SNEAK_ENABLED_MESSAGE = "sneakEnabled";
    public static final String SNEAK_DISABLED_MESSAGE = "sneakDisabled";
    public static final String CONFIRMATION_REQUIRED_IDLE_MESSAGE = "confirmationRequiredIdle";
    public static final String CONFIRMATION_REQUIRED_NO_LEAVES_MESSAGE = "confirmationRequiredNoLeaves";
    public static final String CONFIRMATION_REQUIRED_BOTH_MESSAGE = "confirmationRequiredBoth";
    public static final String CONFIRMATION_SUCCESS_MESSAGE = "confirmationSuccess";
    public static final String NO_PENDING_CONFIRMATION_MESSAGE = "noPendingConfirmation";
    public static final String ABOUT_HEADER = "aboutHeader";
    public static final String ABOUT_LICENSE = "aboutLicense";
    public static final String ABOUT_GITHUB = "aboutGithub";
    public static final String ABOUT_DISCORD = "aboutDiscord";
    public static final String ABOUT_MODRINTH = "aboutModrinth";

    private static final long SAVE_INTERVAL = 1200L; // 60s
    private static final int SAVE_THRESHOLD = 15;

    private static AutoTreeChop instance;

    private Config config;
    private AutoTreeChopAPI autoTreeChopAPI;
    private Map<UUID, PlayerConfig> playerConfigs = new ConcurrentHashMap<>();
    private Metrics metrics;
    private TranslationManager translationManager;
    private ConfirmationManager confirmationManager;

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

    /**
     * Sends a translated message to a command sender
     */
    public static void sendMessage(CommandSender sender, String messageKey, TagResolver... resolvers) {
        if (instance != null && instance.translationManager != null) {
            instance.translationManager.sendMessage(sender, messageKey, resolvers);
        }
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
        instance = this;

        saveDefaultConfig();
        config = new Config(this);

        metrics = new Metrics(this, 20053);

        // Register event listeners
        registerEvents();

        // Register commands
        var lamp = BukkitLamp.builder(this).build();
        lamp.register(new ReloadCommand(this, config));
        lamp.register(new AboutCommand(this));
        lamp.register(new ToggleCommand(this));
        lamp.register(new MainCommand(this));
        lamp.register(new UsageCommand(this, config));
        lamp.register(new ConfirmCommand(this));

        // Initialize translation system
        translationManager = new TranslationManager(this);
        loadLocale();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new AutoTreeChopExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion for AutoTreeChop has been registered.");
        }

        new ModrinthUpdateChecker(this, "autotreechop", "paper")
                .checkEveryXHours(24)
                .setDonationLink("https://ko-fi.com/maoyue")
                .setChangelogLink("https://modrinth.com/plugin/autotreechop/changelog")
                .setDownloadLink("https://modrinth.com/plugin/autotreechop/versions")
                .setNotifyOpsOnJoin(true)
                .setNotifyByPermissionOnJoin("autotreechop.updatechecker")
                .checkNow();

        databaseManager = new DatabaseManager(
                this,
                config.isUseMysql(),
                config.getHostname(),
                config.getPort(),
                config.getDatabase(),
                config.getUsername(),
                config.getPassword());

        saveTask = new PlayerDataSaveTask(this, SAVE_THRESHOLD);
        saveTask.runTaskTimerAsynchronously(this, SAVE_INTERVAL, SAVE_INTERVAL);

        autoTreeChopAPI = new AutoTreeChopAPI(this);
        playerConfigs = new ConcurrentHashMap<>();
        initializeHooks();

        cooldownManager = new CooldownManager();

        confirmationManager = new ConfirmationManager(this);

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
                getLogger()
                        .warning(
                                "Residence can't be hook, please report this to our GitHub: https://github.com/milkteamc/AutoTreeChop/issues");
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
                getLogger()
                        .warning(
                                "GriefPrevention can't be hook, please report this to our GitHub: https://github.com/milkteamc/AutoTreeChop/issues");
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
                getLogger()
                        .warning(
                                "Lands can't be hook, please report this to our GitHub: https://github.com/milkteamc/AutoTreeChop/issues");
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
                getLogger()
                        .warning(
                                "WorldGuard can't be hook, please report this to our GitHub: https://github.com/milkteamc/AutoTreeChop/issues");
                worldGuardEnabled = false;
            }
        } else {
            worldGuardEnabled = false;
        }
    }

    private void loadLocale() {
        saveResourceIfNotExists("lang/styles.properties");
        saveResourceIfNotExists("lang/en.properties");
        saveResourceIfNotExists("lang/de.properties");
        saveResourceIfNotExists("lang/es.properties");
        saveResourceIfNotExists("lang/fr.properties");
        saveResourceIfNotExists("lang/ja.properties");
        saveResourceIfNotExists("lang/ru.properties");
        saveResourceIfNotExists("lang/zh.properties");
        saveResourceIfNotExists("lang/ms.properties");

        Locale defaultLocale = config.getLocale() == null ? Locale.getDefault() : config.getLocale();
        translationManager.initialize(defaultLocale, config.isUseClientLocale());
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
            confirmationManager.clearPlayer(entry.getKey());
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

        if (translationManager != null) {
            translationManager.close();
        }

        metrics.shutdown();

        getLogger().info("AutoTreeChop disabled!");
    }

    public PlayerConfig getPlayerConfig(UUID playerUUID) {
        PlayerConfig playerConfig = playerConfigs.get(playerUUID);

        if (playerConfig == null) {
            getLogger().warning("PlayerConfig not found for " + playerUUID + ", loading synchronously");
            try {
                DatabaseManager.PlayerData data = databaseManager
                        .loadPlayerDataAsync(playerUUID, config.getDefaultTreeChop())
                        .get();

                playerConfig = new PlayerConfig(playerUUID, data);
                playerConfigs.put(playerUUID, playerConfig);
            } catch (Exception e) {
                getLogger().warning("Failed to load player data: " + e.getMessage());
                DatabaseManager.PlayerData defaultData = new DatabaseManager.PlayerData(
                        playerUUID, config.getDefaultTreeChop(), 0, 0, java.time.LocalDate.now());
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

    public TranslationManager getTranslationManager() {
        return translationManager;
    }

    public ConfirmationManager getConfirmationManager() {
        return confirmationManager;
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

    public static AutoTreeChop getInstance() {
        return instance;
    }
}
