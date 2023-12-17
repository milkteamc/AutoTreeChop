package org.milkteamc.autotreechop;

import cn.handyplus.lib.adapter.HandySchedulerUtil;
import com.jeff_media.updatechecker.UpdateCheckSource;
import com.jeff_media.updatechecker.UpdateChecker;
import com.jeff_media.updatechecker.UserAgentBuilder;
import de.cubbossa.translations.Message;
import de.cubbossa.translations.MessageBuilder;
import de.cubbossa.translations.Translations;
import de.cubbossa.translations.TranslationsFramework;
import de.cubbossa.translations.persistent.PropertiesMessageStorage;
import de.cubbossa.translations.persistent.PropertiesStyleStorage;
import net.kyori.adventure.platform.AudienceProvider;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static cn.handyplus.lib.adapter.HandySchedulerUtil.isFolia;

public class AutoTreeChop extends JavaPlugin implements Listener, CommandExecutor {

    private Map<UUID, PlayerConfig> playerConfigs;
    private AutoTreeChopAPI api;

    public static final Message ENABLED_MESSAGE = new MessageBuilder("enabled")
            .withDefault("<positive>Auto tree chopping enabled.</positive>").build();
    public static final Message DISABLED_MESSAGE = new MessageBuilder("disabled")
            .withDefault("<negative>Auto tree chopping disabled.</negative>").build();
    public static final Message NO_PERMISSION_MESSAGE = new MessageBuilder("no-permission")
            .withDefault("<negative>You don't have permission to use this command.</negative>").build();
    public static final Message HIT_MAX_USAGE_MESSAGE = new MessageBuilder("hitmaxusage")
            .withDefault("<negative>You've reached the daily usage limit.</negative>").build();
    public static final Message HIT_MAX_BLOCK_MESSAGE = new MessageBuilder("hitmaxblock")
            .withDefault("<negative>You have reached your daily block breaking limit.</negative>").build();
    public static final Message USAGE_MESSAGE = new MessageBuilder("usage")
            .withDefault("<positive>You have used the AutoTreeChop <current_uses>/<max_uses> times today.</positive>").build();
    public static final Message BLOCKS_BROKEN_MESSAGE = new MessageBuilder("blocks-broken")
            .withDefault("<positive>You have broken <current_blocks>/<max_blocks> blocks today.</positive>").build();
    public static final Message ENABLED_BY_OTHER_MESSAGE = new MessageBuilder("enabledByOther")
            .withDefault("<positive>Auto tree chopping enabled by <player>.</positive>").build();
    public static final Message ENABLED_FOR_OTHER_MESSAGE = new MessageBuilder("enabledForOther")
            .withDefault("<positive>Auto tree chopping enabled for <player></positive>").build();
    public static final Message DISABLED_BY_OTHER_MESSAGE = new MessageBuilder("disabledByOther")
            .withDefault("<negative>Auto tree chopping disabled by <player>.</negative>").build();
    public static final Message DISABLED_FOR_OTHER_MESSAGE = new MessageBuilder("disabledForOther")
            .withDefault("<negative>Auto tree chopping disabled for <player></negative>").build();
    public static final Message CONSOLE_NAME = new MessageBuilder("consoleName")
            .withDefault("console").build();

    private boolean VisualEffect;
    private boolean toolDamage;

    private int maxUsesPerDay;
    private int maxBlocksPerDay;

    private boolean stopChoppingIfNotConnected;
    private boolean stopChoppingIfDifferentTypes;

    private Locale locale;
    private AudienceProvider audienceProvider;
    private Translations translations;

    private static final String SPIGOT_RESOURCE_ID = "113071";

    public void sendMessage(CommandSender sender, ComponentLike message) {
        if (sender instanceof Player player) {
            audienceProvider.player(player.getUniqueId()).sendMessage(message);
            return;
        }
        audienceProvider.console().sendMessage(message);
    }

    private FileConfiguration loadConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        FileConfiguration defaultConfig;

        defaultConfig = new YamlConfiguration();
        defaultConfig.set("visual-effect", true);
        defaultConfig.set("toolDamage", true);
        defaultConfig.set("max-uses-per-day", 50);
        defaultConfig.set("max-blocks-per-day", 500);
        defaultConfig.set("stopChoppingIfNotConnected", false);
        defaultConfig.set("stopChoppingIfDifferentTypes", false);
        defaultConfig.set("locale", Locale.ENGLISH);

        if (!configFile.exists()) {
            try {
                if (!configFile.getParentFile().exists()) {
                    configFile.getParentFile().mkdirs();
                }
                configFile.createNewFile();
            } catch (IOException e) {
                getLogger().warning("An error occurred:" + e);
                return defaultConfig;
            }
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        for (String key : defaultConfig.getKeys(true)) {
            if (!config.contains(key)) {
                config.set(key, defaultConfig.get(key));
            }
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().warning("An error occurred:" + e);
        }

        VisualEffect = config.getBoolean("visual-effect");
        toolDamage = config.getBoolean("toolDamage");
        maxUsesPerDay = config.getInt("max-uses-per-day");
        maxBlocksPerDay = config.getInt("max-blocks-per-day");
        stopChoppingIfNotConnected = config.getBoolean("stopChoppingIfNotConnected");
        stopChoppingIfDifferentTypes = config.getBoolean("stopChoppingIfDifferentTypes");
        Object locale = config.get("locale");
        if (locale instanceof String s) {
            this.locale = Locale.forLanguageTag(s);
        }
        else if (locale instanceof Locale l) {
            this.locale = l;
        }
      
        return config;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command cmd, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (cmd.getName().equalsIgnoreCase("autotreechop")) {
            if (args.length == 1) {

                completions.add("usage");
                if (sender.hasPermission("autotreechop.other") || sender.hasPermission("autotreechop.op")) {
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        completions.add(onlinePlayer.getName());
                    }
                }
                if (sender.hasPermission("autotreechop.op")) {
                    completions.add("reload");
                }

                return completions;
            }
        }

        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("autotreechop")) {
            PlayerConfig playerConfig;

            if (sender instanceof Player player) {
                // Check if the player has the required permission
                if (!player.hasPermission("autotreechop.use")) {
                    sendMessage(player, NO_PERMISSION_MESSAGE);
                    return true;
                }

                // Inside the onCommand method
                if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                    if (sender.hasPermission("autotreechop.reload")) {
                        loadConfig();

                        translations.loadStyles();
                        translations.loadLocales();

                        sender.sendMessage("Config reloaded successfully.");
                    } else {
                        sendMessage(sender, NO_PERMISSION_MESSAGE);
                    }
                    return true;
                }


                // If the user provided "usage" as an argument
                if (args.length > 0 && args[0].equalsIgnoreCase("usage")) {
                    playerConfig = getPlayerConfig(player.getUniqueId()); // Get playerConfig for sender
                    sendMessage(player, USAGE_MESSAGE.formatted(
                            Formatter.number("current_uses", playerConfig.getDailyUses()),
                            Formatter.number("max_uses", maxUsesPerDay)
                    ));
                    sendMessage(player, BLOCKS_BROKEN_MESSAGE.formatted(
                            Formatter.number("current_blocks", playerConfig.getDailyBlocksBroken()),
                            Formatter.number("max_blocks", maxBlocksPerDay)
                    ));
                    return true;
                }

                // Check if the user provided a player name
                if (args.length > 0) {
                    // Get the target player
                    Player targetPlayer = Bukkit.getPlayer(args[0]);

                    // Check if the target player is online
                    if (targetPlayer != null) {
                        // Check if the sender has the required permission to toggle other players' state
                        if (player.hasPermission("autotreechop.other") || player.hasPermission("autotreechop.op")) {
                            UUID targetUUID = targetPlayer.getUniqueId();
                            playerConfig = getPlayerConfig(targetUUID);

                            boolean autoTreeChopEnabled = !playerConfig.isAutoTreeChopEnabled();
                            playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);

                            if (autoTreeChopEnabled) {
                                sendMessage(player, ENABLED_FOR_OTHER_MESSAGE.formatted(
                                        Placeholder.parsed("player", targetPlayer.getName())
                                ));
                                sendMessage(targetPlayer, ENABLED_BY_OTHER_MESSAGE.formatted(
                                        Placeholder.parsed("player", player.getName())
                                ));
                            } else {
                                sendMessage(player, DISABLED_FOR_OTHER_MESSAGE.formatted(
                                        Placeholder.parsed("player", targetPlayer.getName())
                                ));
                                sendMessage(targetPlayer, DISABLED_BY_OTHER_MESSAGE.formatted(
                                        Placeholder.parsed("player", player.getName())
                                ));
                            }
                        } else {
                            sendMessage(player, NO_PERMISSION_MESSAGE);
                        }
                    } else {
                        player.sendMessage("Player not found: " + args[0]);
                    }
                    return true;
                } else {
                    // Toggle the state for the sender
                    playerConfig = getPlayerConfig(player.getUniqueId()); // Get playerConfig for sender
                    boolean autoTreeChopEnabled = !playerConfig.isAutoTreeChopEnabled();
                    playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);

                    if (autoTreeChopEnabled) {
                        sendMessage(player, ENABLED_MESSAGE);
                    } else {
                        sendMessage(player, DISABLED_MESSAGE);
                    }
                }

            } else {
                if (args.length > 0) {
                    // Get the target player
                    Player targetPlayer = Bukkit.getPlayer(args[0]);

                    // Check if the target player is online
                    if (targetPlayer != null) {
                        UUID targetUUID = targetPlayer.getUniqueId();
                        playerConfig = getPlayerConfig(targetUUID);

                        boolean autoTreeChopEnabled = !playerConfig.isAutoTreeChopEnabled();
                        playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);

                        if (autoTreeChopEnabled) {
                            sendMessage(sender, ENABLED_FOR_OTHER_MESSAGE.formatted(
                                    Placeholder.parsed("player", targetPlayer.getName())
                            ));
                            sendMessage(targetPlayer, ENABLED_BY_OTHER_MESSAGE.formatted(
                                    Placeholder.component("player", CONSOLE_NAME)
                            ));
                        } else {
                            sendMessage(sender, DISABLED_FOR_OTHER_MESSAGE.formatted(
                                    Placeholder.parsed("player", targetPlayer.getName())
                            ));
                            sendMessage(targetPlayer, DISABLED_BY_OTHER_MESSAGE.formatted(
                                    Placeholder.component("player", CONSOLE_NAME)
                            ));
                        }
                    } else {
                        getLogger().warning("Player not found: " + args[1]);
                    }
                    return true;
                } else {
                    sender.sendMessage("Only players can use this command.");
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void onEnable() {
        org.milkteamc.autotreechop.Metrics metrics = new Metrics(this, 20053); //bstats

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("autotreechop").setExecutor(this);

        saveDefaultConfig();
        loadConfig();

        audienceProvider = BukkitAudiences.create(this);
        TranslationsFramework.enable(new File(getDataFolder(), "/.."));
        translations = TranslationsFramework.application("AutoTreeChop");
        // always use the configured locale, no matter what user.
        translations.setLocaleProvider(audience -> locale == null ? Locale.ENGLISH : locale);

        translations.setMessageStorage(new PropertiesMessageStorage(new File(getDataFolder(), "/lang/")));
        translations.setStyleStorage(new PropertiesStyleStorage(new File(getDataFolder(), "/lang/styles.properties")));

        // Register all messages from this class and save them into an en.properties and a de.properties.
        // If already exists, this will only write missing values into these files.
        translations.addMessages(TranslationsFramework.messageFieldsFromClass(AutoTreeChop.class));
        translations.saveLocale(Locale.ENGLISH);
        saveResource("lang/de.properties", false);
        saveResource("lang/zh.properties", false);
        // Now load all written and also all pre-existing translations (in case the user added some)
        translations.loadLocales();

        translations.loadStyles();
        // Let's make <negative> a resolver for red color and <positive> for green.
        // We can simply modify the styles.properties file to change the whole look and feel of the plugin.
        if (!translations.getStyleSet().containsKey("negative")) {
            translations.getStyleSet().put("negative", Style.style(NamedTextColor.RED));
        }
        if (!translations.getStyleSet().containsKey("positive")) {
            translations.getStyleSet().put("positive", Style.style(NamedTextColor.GREEN));
        }
        // Save potential changes
        translations.saveStyles();
        // Now ready to use sendMessage method


        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new AutoTreeChopExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion for AutoTreeChop has been registered.");
        } else {
            getLogger().warning("PlaceholderAPI not found. Placeholder expansion for AutoTreeChop will not work.");
        }

        HandySchedulerUtil.init(this);

        if (!isFolia()) {
            CheckUpdate();
        } else {
            getLogger().warning("It seen you are using Folia, some function may not work.");
        }
        api = new AutoTreeChopAPI(this);
        playerConfigs = new HashMap<>();
    }

    @Override
    public void onDisable() {
        translations.close();
        audienceProvider.close();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        PlayerConfig playerConfig = getPlayerConfig(playerUUID);

        Block block = event.getBlock();
        Material material = block.getType();

        if (playerConfig.isAutoTreeChopEnabled() && isLog(material)) {

            if (!player.hasPermission("autotreechop.vip") && playerConfig.getDailyBlocksBroken() >= maxBlocksPerDay) {
                sendMaxBlockLimitReachedMessage(player, block);
                event.setCancelled(true);
                return;
            }

            if (!player.hasPermission("autotreechop.vip") && playerConfig.getDailyUses() >= maxUsesPerDay) {
                sendMessage(player, HIT_MAX_USAGE_MESSAGE);
                return;
            }

            if (VisualEffect) {
                showChopEffect(player, block);
            }

            event.setCancelled(true);
            checkedLocations.clear();
            if (stopChoppingIfNotConnected) {
                chopTreeConnectedBlocks(block, player);
            } else {
                chopTree(block, player);
            }
            checkedLocations.clear();

            playerConfig.incrementDailyUses();
        }
    }

    private void CheckUpdate() {
        new UpdateChecker(this, UpdateCheckSource.SPIGOT, SPIGOT_RESOURCE_ID) // You can also use Spiget instead of Spigot - Spiget's API is usually much faster up to date.
                .checkEveryXHours(24) // Check every 24 hours
                .setDonationLink("https://ko-fi.com/maoyue")
                .setChangelogLink(SPIGOT_RESOURCE_ID) // Same as for the Download link: URL or Spigot Resource ID
                .setNotifyOpsOnJoin(true) // Notify OPs on Join when a new version is found (default)
                .setNotifyByPermissionOnJoin("autotreechop.updatechecker") // Also notify people on join with this permission
                .setUserAgent(new UserAgentBuilder().addPluginNameAndVersion())
                .checkNow(); // And check right now
    }

    // Sends a message to the player and shows a red particle effect indicating the block limit has been reached
    private void sendMaxBlockLimitReachedMessage(Player player, Block block) {
        sendMessage(player, HIT_MAX_BLOCK_MESSAGE);
        player.getWorld().spawnParticle(org.bukkit.Particle.REDSTONE, block.getLocation().add(0.5, 0.5, 0.5), 50, 0.5, 0.5, 0.5, 0, new Particle.DustOptions(Color.RED, 1));
    }

    // Shows a green particle effect indicating the block has been chopped
    private void showChopEffect(Player player, Block block) {
        player.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, block.getLocation().add(0.5, 0.5, 0.5), 50, 0.5, 0.5, 0.5, 0);
    }

    // Method to reduce the durability value of tools
    private void damageTool(Player player, int amount) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool != null && tool.getType().getMaxDurability() > 0) {
            int newDurability = tool.getDurability() + amount;
            if (newDurability > tool.getType().getMaxDurability()) {
                player.getInventory().setItemInMainHand(null);
            } else {
                tool.setDurability((short) newDurability);
            }
        }
    }

    private Set<Location> checkedLocations = new HashSet<>();

    private void chopTree(Block block, Player player) {
        UUID playerUUID = player.getUniqueId();
        PlayerConfig playerConfig = getPlayerConfig(playerUUID);
        if (checkedLocations.contains(block.getLocation())) {
            return;
        }
        checkedLocations.add(block.getLocation());

        if (isLog(block.getType())) {
            block.breakNaturally();
        } else {
            return;
        }

        playerConfig.incrementDailyBlocksBroken();
        if (toolDamage) {
            damageTool(player, 1);
        }

        HandySchedulerUtil.runTaskAsynchronously(() -> {
            for (int yOffset = -1; yOffset <= 1; yOffset++) {
                for (int xOffset = -1; xOffset <= 1; xOffset++) {
                    for (int zOffset = -1; zOffset <= 1; zOffset++) {
                        if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
                            continue;
                        }
                        Block relativeBlock = block.getRelative(xOffset, yOffset, zOffset);
                        if (stopChoppingIfDifferentTypes && !isSameType(block.getType(), relativeBlock.getType())) {
                            continue;
                        }
                        HandySchedulerUtil.runTask(() -> chopTree(relativeBlock, player));
                    }
                }
            }
        });
    }

    private void chopTreeConnectedBlocks(Block block, Player player) {
        UUID playerUUID = player.getUniqueId();
        PlayerConfig playerConfig = getPlayerConfig(playerUUID);
        if (checkedLocations.contains(block.getLocation())) {
            return;
        }
        checkedLocations.add(block.getLocation());

        if (isLog(block.getType())) {
            block.breakNaturally();
        } else {
            return;
        }

        playerConfig.incrementDailyBlocksBroken();
        if (toolDamage) {
            damageTool(player, 1);
        }

        HandySchedulerUtil.runTaskAsynchronously(() -> {
            for (int yOffset = -1; yOffset <= 1; yOffset++) {
                for (int xOffset = -1; xOffset <= 1; xOffset++) {
                    for (int zOffset = -1; zOffset <= 1; zOffset++) {
                        if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
                            continue;
                        }
                        Block relativeBlock = block.getRelative(xOffset, yOffset, zOffset);
                        if (stopChoppingIfDifferentTypes && !isSameType(block.getType(), relativeBlock.getType())) {
                            continue;
                        }
                        HandySchedulerUtil.runTask(() -> chopTree(relativeBlock, player));
                    }
                }
            }
        });
    }

    // Add a new method to check if two block types are the same
    private boolean isSameType(Material type1, Material type2) {
        return type1 == type2;
    }


    private boolean isLog(Material material) {
        return material == Material.OAK_LOG ||
                material == Material.SPRUCE_LOG ||
                material == Material.BIRCH_LOG ||
                material == Material.JUNGLE_LOG ||
                material == Material.ACACIA_LOG ||
                material == Material.DARK_OAK_LOG ||
                material == Material.MANGROVE_LOG ||
                material == Material.CHERRY_LOG;
    }

    PlayerConfig getPlayerConfig(UUID playerUUID) {
        PlayerConfig playerConfig = playerConfigs.get(playerUUID);
        if (playerConfig == null) {
            playerConfig = new PlayerConfig(playerUUID);
            playerConfigs.put(playerUUID, playerConfig);
        }
        return playerConfig;
    }

    public int getPlayerDailyUses(UUID playerUUID) {
        return getPlayerConfig(playerUUID).getDailyUses();
    }

    public int getPlayerDailyBlocksBroken(UUID playerUUID) {
        return getPlayerConfig(playerUUID).getDailyBlocksBroken();
    }

    public AutoTreeChopAPI getAPI() {
        return api;
    }
}