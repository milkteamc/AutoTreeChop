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

import java.io.File;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.milkteamc.autotreechop.command.AboutCommand;
import org.milkteamc.autotreechop.command.ConfirmCommand;
import org.milkteamc.autotreechop.command.ReloadCommand;
import org.milkteamc.autotreechop.command.ToggleCommand;
import org.milkteamc.autotreechop.command.UsageCommand;
import org.milkteamc.autotreechop.database.DataManager;
import org.milkteamc.autotreechop.database.DatabaseManager;
import org.milkteamc.autotreechop.events.BlockBreakListener;
import org.milkteamc.autotreechop.events.PlayerJoinListener;
import org.milkteamc.autotreechop.events.PlayerQuitListener;
import org.milkteamc.autotreechop.events.PlayerSneakListener;
import org.milkteamc.autotreechop.hooks.HookManager;
import org.milkteamc.autotreechop.translation.TranslationManager;
import org.milkteamc.autotreechop.updater.ModrinthUpdateChecker;
import org.milkteamc.autotreechop.utils.ConfirmationManager;
import org.milkteamc.autotreechop.utils.CooldownManager;
import org.milkteamc.autotreechop.utils.TreeChopUtils;
import revxrsal.commands.bukkit.BukkitLamp;

public class AutoTreeChop extends JavaPlugin {

    private static AutoTreeChop instance;

    private Config config;
    private DatabaseManager databaseManager;
    private DataManager dataManager;
    private HookManager hookManager;
    private TranslationManager translationManager;

    private AutoTreeChopAPI autoTreeChopAPI;
    private ConfirmationManager confirmationManager;
    private CooldownManager cooldownManager;
    private TreeChopUtils treeChopUtils;
    private ModrinthUpdateChecker updateChecker;
    private Metrics metrics;
    private PluginDescriptionFile description;

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static void sendMessage(CommandSender sender, String messageKey, TagResolver... resolvers) {
        if (instance != null && instance.translationManager != null) {
            instance.translationManager.sendMessage(sender, messageKey, resolvers);
        }
    }

    @Override
    public void onLoad() {
        @SuppressWarnings("deprecation")
        PluginDescriptionFile desc = getDescription();
        this.description = desc;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.config = new Config(this);
        setupTranslation();

        this.cooldownManager = new CooldownManager();
        this.confirmationManager = new ConfirmationManager(this);
        this.treeChopUtils = new TreeChopUtils(this);
        this.autoTreeChopAPI = new AutoTreeChopAPI(this);

        this.hookManager = new HookManager(this, config);

        setupDatabase();
        this.dataManager = new DataManager(this, databaseManager, confirmationManager);
        this.dataManager.startSaveTask();

        registerEvents();
        registerCommands();

        setupIntegrations();

        getLogger().info("AutoTreeChop enabled!");
    }

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.shutdown();
        }

        if (translationManager != null) {
            translationManager.close();
        }

        if (metrics != null) {
            metrics.shutdown();
        }

        getLogger().info("AutoTreeChop disabled!");
    }

    private void setupTranslation() {
        this.translationManager = new TranslationManager(this);
        String[] langs = {"styles", "en", "de", "es", "fr", "ja", "ru", "zh", "ms"};
        for (String lang : langs) {
            saveResourceIfNotExists("lang/" + lang + ".properties");
        }
        Locale defaultLocale = config.getLocale() == null ? Locale.getDefault() : config.getLocale();
        translationManager.initialize(defaultLocale, config.isUseClientLocale());
    }

    private void saveResourceIfNotExists(String resourcePath) {
        if (!new File(getDataFolder(), resourcePath).exists()) {
            saveResource(resourcePath, false);
        }
    }

    private void setupDatabase() {
        this.databaseManager = new DatabaseManager(
                this,
                config.isUseMysql(),
                config.getHostname(),
                config.getPort(),
                config.getDatabase(),
                config.getUsername(),
                config.getPassword());
    }

    private void registerEvents() {
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerSneakListener(this), this);
    }

    private void registerCommands() {
        var lamp = BukkitLamp.builder(this).build();
        lamp.register(new ReloadCommand(this, config));
        lamp.register(new AboutCommand(this));
        lamp.register(new ToggleCommand(this));
        lamp.register(new UsageCommand(this, config));
        lamp.register(new ConfirmCommand(this));
    }

    private void setupIntegrations() {
        this.metrics = new Metrics(this, 20053);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new AutoTreeChopExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion for AutoTreeChop has been registered.");
        }

        this.updateChecker = new ModrinthUpdateChecker(this, "autotreechop", "paper")
                .setDonationLink("https://ko-fi.com/maoyue")
                .setChangelogLink("https://modrinth.com/plugin/autotreechop/changelog")
                .setDownloadLink("https://modrinth.com/plugin/autotreechop/versions")
                .setNotifyOpsOnJoin(true)
                .setNotifyByPermissionOnJoin("autotreechop.updatechecker")
                .startPeriodicCheck();
    }

    public static AutoTreeChop getInstance() {
        return instance;
    }

    public Config getPluginConfig() {
        return config;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    public AutoTreeChopAPI getAutoTreeChopAPI() {
        return autoTreeChopAPI;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public ConfirmationManager getConfirmationManager() {
        return confirmationManager;
    }

    public TreeChopUtils getTreeChopUtils() {
        return treeChopUtils;
    }

    public TranslationManager getTranslationManager() {
        return translationManager;
    }

    public ModrinthUpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public PluginDescriptionFile getPluginDescription() {
        return description;
    }
}
