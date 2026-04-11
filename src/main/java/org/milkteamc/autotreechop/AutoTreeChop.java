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
 
package org.milkteamc.autotreechop;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
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
import org.milkteamc.autotreechop.updater.ModrinthUpdateChecker;
import org.milkteamc.autotreechop.utils.ConfirmationManager;
import org.milkteamc.autotreechop.utils.CooldownManager;
import org.milkteamc.autotreechop.utils.SessionManager;
import org.milkteamc.autotreechop.utils.TreeChopUtils;
import revxrsal.commands.bukkit.BukkitLamp;

public class AutoTreeChop extends JavaPlugin {

    private static final long SAVE_INTERVAL = 1200L; // 60s
    private static final int SAVE_THRESHOLD = 15;

    private static AutoTreeChop instance;

    private Config config;
    private AutoTreeChopAPI autoTreeChopAPI;
    private Map<UUID, PlayerConfig> playerConfigs = new ConcurrentHashMap<>();
    private Metrics metrics;
    private TranslationManager translationManager;
    private ConfirmationManager confirmationManager;
    private ModrinthUpdateChecker updateChecker;

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

        // Initialize translation system
        translationManager = new TranslationManager(this);
        loadLocale();

        // Register commands
        var lamp = BukkitLamp.builder(this).build();
        lamp.register(new ReloadCommand(this, config));
        lamp.register(new AboutCommand(this));
        lamp.register(new ToggleCommand(this));
        lamp.register(new UsageCommand(this, config));
        lamp.register(new ConfirmCommand(this));

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new AutoTreeChopExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion for AutoTreeChop has been registered.");
        }

        updateChecker = new ModrinthUpdateChecker(this, "autotreechop", "paper")
                .setDonationLink("https://ko-fi.com/maoyue")
                .setChangelogLink("https://modrinth.com/plugin/autotreechop/changelog")
                .setDownloadLink("https://modrinth.com/plugin/autotreechop/versions")
                .setNotifyOpsOnJoin(true)
                .setNotifyByPermissionOnJoin("autotreechop.updatechecker")
                .startPeriodicCheck();

        databaseManager = new DatabaseManager(
                this,
                config.isUseMysql(),
                config.getHostname(),
                config.getPort(),
                config.getDatabase(),
                config.getUsername(),
                config.getPassword());

        saveTask = new PlayerDataSaveTask(this, SAVE_THRESHOLD);
        UniversalScheduler.getScheduler(this).runTaskTimerAsynchronously(saveTask, SAVE_INTERVAL, SAVE_INTERVAL);
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
            try {
                saveTask.cancel();
            } catch (IllegalStateException ignored) {
                // Task was never scheduled or already cancelled (e.g. Folia shutdown)
            }
        }

        if (confirmationManager != null && playerConfigs != null) {
            for (Map.Entry<UUID, PlayerConfig> entry : playerConfigs.entrySet()) {
                confirmationManager.clearPlayer(entry.getKey());
                if (entry.getValue().isDirty()) {
                    databaseManager.savePlayerDataSync(entry.getValue().getData());
                }
            }
            playerConfigs.clear();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        if (playerConfigs != null) {
            SessionManager sessionManager = SessionManager.getInstance();
            for (UUID uuid : new HashSet<>(playerConfigs.keySet())) {
                sessionManager.clearAllPlayerSessions(uuid);
            }
        }

        if (translationManager != null) {
            translationManager.close();
        }

        if (metrics != null) {
            metrics.shutdown();
        }

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

    public ModrinthUpdateChecker getUpdateChecker() {
        return updateChecker;
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
