package org.milkteamc.autotreechop;

import com.bekvon.bukkit.residence.api.ResidenceApi;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.jeff_media.updatechecker.UpdateCheckSource;
import com.jeff_media.updatechecker.UpdateChecker;
import com.jeff_media.updatechecker.UserAgentBuilder;
import de.cubbossa.tinytranslations.*;
import de.cubbossa.tinytranslations.libs.kyori.adventure.text.ComponentLike;
import de.cubbossa.tinytranslations.storage.properties.PropertiesMessageStorage;
import de.cubbossa.tinytranslations.storage.properties.PropertiesStyleStorage;
import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.land.LandWorld;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
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
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class AutoTreeChop extends JavaPlugin implements Listener, CommandExecutor {

    // We make a prefix just to be safe if sm removes our style overrides. Then each plugin message begins with prefix
    // and if none set it will look ugly. We don't need to add decoration (like "[AutoTreeChop] >"), it's done via styling
    public static final Message PREFIX = Message.message("prefix", "AutoTreeChop");
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

    private static final String SPIGOT_RESOURCE_ID = "113071";
    private static final List<String> SUPPORTED_VERSIONS = Arrays.asList(
            "1.21.1", "1.21",
            "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
            "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
            "1.18.2", "1.18.1", "1.18",
            "1.17.1", "1.17"
    );
    private String bukkitVersion = this.getServer().getBukkitVersion();
    private final Set<Location> checkedLocations = new HashSet<>();
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private Metrics metrics;
    private Map<UUID, PlayerConfig> playerConfigs;
    private AutoTreeChopAPI api;
    private boolean VisualEffect;
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
    private MessageTranslator translations;
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
    private Set<Material> logTypes;
    private int toolDamageDecrease;

    public static void sendMessage(CommandSender sender, ComponentLike message) {
        BukkitTinyTranslations.sendMessageIfNotEmpty(sender, message);
    }

    // Auto edit add missing key
    @NotNull
    private static FileConfiguration getDefaultConfig() {
        FileConfiguration defaultConfig;

        defaultConfig = new YamlConfiguration();
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
        defaultConfig.set("locale", Locale.ENGLISH);
        defaultConfig.set("residenceFlag", "build");
        defaultConfig.set("griefPreventionFlag", "Build");
        defaultConfig.set("limitVipUsage", true);
        defaultConfig.set("vip-uses-per-day", 50);
        defaultConfig.set("vip-blocks-per-day", 500);
        defaultConfig.set("toolDamageDecrease", 1);
        defaultConfig.set("log-types", Arrays.asList("OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "JUNGLE_LOG", "ACACIA_LOG", "DARK_OAK_LOG", "MANGROVE_LOG", "CHERRY_LOG"));
        return defaultConfig;
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // Sends a message to the player and shows a red particle effect indicating the block limit has been reached
    private static void sendMaxBlockLimitReachedMessage(Player player, Block block) {
        sendMessage(player, HIT_MAX_BLOCK_MESSAGE);
        player.getWorld().spawnParticle(Particle.REDSTONE, block.getLocation().add(0.5, 0.5, 0.5), 50, 0.5, 0.5, 0.5, 0, new Particle.DustOptions(Color.RED, 1));
    }

    private FileConfiguration loadConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        FileConfiguration defaultConfig = getDefaultConfig();

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
        chopTreeAsync = config.getBoolean("chopTreeAsync");
        Object locale = config.get("locale");
        residenceFlag = config.getString("residenceFlag");
        griefPreventionFlag = config.getString("griefPreventionFlag");
        cooldownTime = config.getInt("cooldownTime");
        vipCooldownTime = config.getInt("vipCooldownTime");
        if (locale instanceof String s) {
            this.locale = Locale.forLanguageTag(s);
        } else if (locale instanceof Locale l) {
            this.locale = l;
        }
        useClientLocale = config.getBoolean("use-player-locale");

        // MySQL
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
        logTypes = logTypeStrings.stream().map(Material::getMaterial).collect(Collectors.toSet());

        return config;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command cmd, @NotNull String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("autotreechop") && !cmd.getName().equalsIgnoreCase("atc")) {
            return null;
        }

        if (args.length != 1) {
            return null;
        }

        List<String> completions = new ArrayList<>();
        completions.add("usage");

        boolean hasOtherPermission = sender.hasPermission("autotreechop.other") || sender.hasPermission("autotreechop.op");
        boolean hasOpPermission = sender.hasPermission("autotreechop.op");

        if (hasOtherPermission) {
            completions.add("enable-all");
            completions.add("disable-all");
            Bukkit.getOnlinePlayers().stream()
                    .limit(10) // Limit to 10 players
                    .forEach(player -> completions.add(player.getName()));
        }

        if (hasOpPermission) {
            completions.add("reload");
        }

        return completions;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command cmd, @NotNull String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("autotreechop") && !cmd.getName().equalsIgnoreCase("atc")) {
            return false;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("autotreechop.reload")) {
                loadConfig();
                loadLocale();
                sender.sendMessage("Config reloaded successfully.");
            } else {
                sendMessage(sender, NO_PERMISSION_MESSAGE);
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("enable-all")) {
            if (sender.hasPermission("autotreechop.other") || sender.hasPermission("autotreechop.op")) {
                toggleAutoTreeChopForAll(sender, true);
                sendMessage(sender, ENABLED_FOR_OTHER_MESSAGE.insertString("player", "everyone"));
            } else {
                sendMessage(sender, NO_PERMISSION_MESSAGE);
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("disable-all")) {
            if (sender.hasPermission("autotreechop.other") || sender.hasPermission("autotreechop.op")) {
                toggleAutoTreeChopForAll(sender, false);
                sendMessage(sender, DISABLED_FOR_OTHER_MESSAGE.insertString("player", "everyone"));
            } else {
                sendMessage(sender, NO_PERMISSION_MESSAGE);
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            if (args.length > 0) {
                handleTargetPlayerToggle(sender, args[0]);
            } else {
                sendMessage(sender, ONLY_PLAYERS_MESSAGE);
            }
            return true;
        }

        if (!player.hasPermission("autotreechop.use")) {
            sendMessage(player, NO_PERMISSION_MESSAGE);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("usage")) {
            handleUsageCommand(player);
            return true;
        }

        if (args.length > 0) {
            handleTargetPlayerToggle(player, args[0]);
            return true;
        }

        toggleAutoTreeChop(player, player.getUniqueId());
        return true;
    }

    private void handleUsageCommand(Player player) {
        if (!player.hasPermission("autotreechop.vip")) {
            PlayerConfig playerConfig = getPlayerConfig(player.getUniqueId());
            BukkitTinyTranslations.sendMessage(player, USAGE_MESSAGE
                    .insertNumber("current_uses", playerConfig.getDailyUses())
                    .insertNumber("max_uses", maxUsesPerDay));
            BukkitTinyTranslations.sendMessage(player, BLOCKS_BROKEN_MESSAGE
                    .insertNumber("current_blocks", playerConfig.getDailyBlocksBroken())
                    .insertNumber("max_blocks", maxBlocksPerDay));
        } else if (player.hasPermission("autotreechop.vip") && limitVipUsage) {
            PlayerConfig playerConfig = getPlayerConfig(player.getUniqueId());
            BukkitTinyTranslations.sendMessage(player, USAGE_MESSAGE
                    .insertNumber("current_uses", playerConfig.getDailyUses())
                    .insertNumber("max_uses", vipUsesPerDay));
            BukkitTinyTranslations.sendMessage(player, BLOCKS_BROKEN_MESSAGE
                    .insertNumber("current_blocks", playerConfig.getDailyBlocksBroken())
                    .insertNumber("max_blocks", vipBlocksPerDay));
        } else if (player.hasPermission("autotreechop.vip") && !limitVipUsage) {
            PlayerConfig playerConfig = getPlayerConfig(player.getUniqueId());
            BukkitTinyTranslations.sendMessage(player, USAGE_MESSAGE
                    .insertNumber("current_uses", playerConfig.getDailyUses())
                    .insertString("max_uses", "\u221e"));
            BukkitTinyTranslations.sendMessage(player, BLOCKS_BROKEN_MESSAGE
                    .insertNumber("current_blocks", playerConfig.getDailyBlocksBroken())
                    .insertString("max_blocks", "\u221e"));
        }
    }

    private void handleTargetPlayerToggle(CommandSender sender, String targetPlayerName) {
        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            sender.sendMessage("Player not found: " + targetPlayerName);
            return;
        }

        UUID targetUUID = targetPlayer.getUniqueId();
        PlayerConfig playerConfig = getPlayerConfig(targetUUID);
        boolean autoTreeChopEnabled = !playerConfig.isAutoTreeChopEnabled();
        playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);

        if (autoTreeChopEnabled) {
            sendMessage(sender, ENABLED_FOR_OTHER_MESSAGE.insertString("player", targetPlayer.getName()));
            sendMessage(targetPlayer, ENABLED_BY_OTHER_MESSAGE.insertString("player", sender.getName()));
        } else {
            sendMessage(sender, DISABLED_FOR_OTHER_MESSAGE.insertString("player", targetPlayer.getName()));
            sendMessage(targetPlayer, DISABLED_BY_OTHER_MESSAGE.insertString("player", sender.getName()));
        }
    }

    private void toggleAutoTreeChop(Player player, UUID playerUUID) {
        PlayerConfig playerConfig = getPlayerConfig(playerUUID);
        boolean autoTreeChopEnabled = !playerConfig.isAutoTreeChopEnabled();
        playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);

        if (autoTreeChopEnabled) {
            BukkitTinyTranslations.sendMessage(player, ENABLED_MESSAGE);
        } else {
            BukkitTinyTranslations.sendMessage(player, DISABLED_MESSAGE);
        }
    }

    // Logic when using /atc enable-all disable-all
    private void toggleAutoTreeChopForAll(CommandSender sender, boolean autoTreeChopEnabled) {
        ComponentLike message = autoTreeChopEnabled
                ? ENABLED_BY_OTHER_MESSAGE.insertString("player", sender.getName())
                : DISABLED_BY_OTHER_MESSAGE.insertString("player", sender.getName());

        // Use parallelStream for better performance
        Bukkit.getOnlinePlayers().parallelStream().forEach(onlinePlayer -> {
            UUID playerUUID = onlinePlayer.getUniqueId();
            PlayerConfig playerConfig = getPlayerConfig(playerUUID);
            playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);
            sendMessage(onlinePlayer, message);
        });
    }

    @Override
    public void onEnable() {
        // Bukkit version checker
        if (bukkitVersion.length() > 14) {
            bukkitVersion = bukkitVersion.substring(0, bukkitVersion.length() - 14);

            if (!SUPPORTED_VERSIONS.contains(bukkitVersion)) {
                getLogger().warning("Your Minecraft version may have some issues, we only fully support "
                        + String.join(", ", SUPPORTED_VERSIONS));
                getLogger().warning("Report any issue to our GitHub: https://github.com/milkteamc/AutoTreeChop/issues");
            }
        }

        metrics = new Metrics(this, 20053); //bstats

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("autotreechop").setExecutor(this);
        getCommand("atc").setExecutor(this);

        saveDefaultConfig();
        loadConfig();

        translations = BukkitTinyTranslations.application(this);

        translations.setMessageStorage(new PropertiesMessageStorage(new File(getDataFolder(), "/lang/")));
        translations.setStyleStorage(new PropertiesStyleStorage(new File(getDataFolder(), "/lang/styles.properties")));

        // Register all messages from this class and save them into an en.properties.
        // If already exists, this will only write missing values into the file.
        translations.addMessages(TinyTranslations.messageFieldsFromClass(AutoTreeChop.class));

        loadLocale();
        // Now ready to use sendMessage method

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new AutoTreeChopExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion for AutoTreeChop has been registered.");
        } else {
            getLogger().warning("PlaceholderAPI not found. Placeholder expansion for AutoTreeChop will not work.");
        }

        if (isFolia()) {
            getLogger().warning("It seen you are using Folia, some function may not work.");
            foliaUpdateChecker();
        } else {
            spigotUpdateChecker();
        }
        api = new AutoTreeChopAPI(this);
        playerConfigs = new HashMap<>();
    }

    private void loadLocale() {

        translations.saveLocale(Locale.ENGLISH);
        saveResourceIfNotExists("lang/styles.properties");
        saveResourceIfNotExists("lang/de.properties");
        saveResourceIfNotExists("lang/zh.properties");

        translations.setUseClientLocale(useClientLocale);
        translations.defaultLocale(locale == null ? Locale.getDefault() : locale);

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
        translations.close();
        metrics.shutdown();
    }

    private void spigotUpdateChecker() {
        new UpdateChecker(this, UpdateCheckSource.SPIGOT, SPIGOT_RESOURCE_ID) // You can also use Spigot instead of Spigot - Spigot's API is usually much faster up to date.
                .checkEveryXHours(24) // Check every 24 hours
                .setDonationLink("https://ko-fi.com/maoyue")
                .setChangelogLink("https://modrinth.com/plugin/autotreechop/version/latest") // Same as for the Download link: URL or Spigot Resource ID
                .setDownloadLink("https://modrinth.com/plugin/autotreechop/version/latest")
                .setNotifyOpsOnJoin(true) // Notify OPs on Join when a new version is found (default)
                .setNotifyByPermissionOnJoin("autotreechop.updatechecker") // Also notify people on join with this permission
                .setUserAgent(new UserAgentBuilder().addPluginNameAndVersion())
                .checkNow(); // And check right now
    }

    private void foliaUpdateChecker() {
        try {

            if (this.getDescription().getVersion().contains("SNAPSHOT")) {
                this.getLogger().warning("You are using a SNAPSHOT version, this should NEVER use in production environment.");
                this.getLogger().warning("Download latest version at: https://modrinth.com/plugin/autotreechop");
                return;
            }

            InputStream inputStream = new URL(("https://api.spigotmc.org/legacy/update.php?resource=113071"))
                    .openStream();
            Scanner scanner = new Scanner(inputStream);
            String version = scanner.next();

            scanner.close();

            String[] currentParts = this.getDescription().getVersion().split("\\.");

            String[] latestParts = version.split("\\.");

            int minLength = Math.min(currentParts.length, latestParts.length);

            for (int i = 0; i < minLength; i++) {

                int currentPart = Integer.parseInt(currentParts[i]);

                int latestPart = Integer.parseInt(latestParts[i]);

                if (currentPart < latestPart) {
                    this.getLogger().warning("A new update available: " + version);
                    this.getLogger().warning("Download now: https://modrinth.com/plugin/autotreechop/version/latest/");
                }
            }
        } catch (Exception e) {
            this.getLogger().log(Level.WARNING,
                    ChatColor.RED + "Cannot check for plugin version: " + e.getMessage());
        }
    }

    // VIP limit checker
    private boolean hasvipUses(Player player, PlayerConfig playerConfig) {
        if (!limitVipUsage) return player.hasPermission("autotreechop.vip");
        if (player.hasPermission("autotreechop.vip")) return playerConfig.getDailyUses() <= vipUsesPerDay;
        return false;
    }

    private boolean hasvipBlock(Player player, PlayerConfig playerConfig) {
        if (!limitVipUsage) return player.hasPermission("autotreechop.vip");
        if (player.hasPermission("autotreechop.vip")) return playerConfig.getDailyBlocksBroken() <= vipBlocksPerDay;
        return false;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        PlayerConfig playerConfig = getPlayerConfig(playerUUID);

        if (isInCooldown(playerUUID)) {
            sendMessage(player, STILL_IN_COOLDOWN_MESSAGE
                    .insertNumber("cooldown_time", getRemainingCooldown(playerUUID))
            );
            event.setCancelled(true);
            return;
        }

        Block block = event.getBlock();
        Material material = block.getType();
        Location location = block.getLocation();
        BlockData blockData = block.getBlockData();

        if (playerConfig.isAutoTreeChopEnabled() && isLog(material)) {

            if (!hasvipBlock(player, playerConfig) && playerConfig.getDailyBlocksBroken() >= maxBlocksPerDay) {
                sendMaxBlockLimitReachedMessage(player, block);
                event.setCancelled(true);
                return;
            }

            if (!hasvipUses(player, playerConfig) && playerConfig.getDailyUses() >= maxUsesPerDay) {
                BukkitTinyTranslations.sendMessage(player, HIT_MAX_USAGE_MESSAGE);
                return;
            }

            if (VisualEffect) {
                showChopEffect(player, block);
            }

            event.setCancelled(true);
            checkedLocations.clear();

            chopTree(block, player, stopChoppingIfNotConnected, location, material, blockData);

            checkedLocations.clear();

            playerConfig.incrementDailyUses();

            // set cooldown time
            setCooldown(player, playerUUID);
        }
    }

    // Shows a green particle effect indicating the block has been chopped
    private void showChopEffect(Player player, Block block) {
        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, block.getLocation().add(0.5, 0.5, 0.5), 50, 0.5, 0.5, 0.5, 0);
    }

    // Method to reduce the durability value of tools
    private void damageTool(Player player, int amount) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType().getMaxDurability() > 0) {
            int newDurability = tool.getDurability() + amount;
            if (newDurability > tool.getType().getMaxDurability()) {
                player.getInventory().setItemInMainHand(null);
            } else {
                tool.setDurability((short) newDurability);
            }
        }
    }

    private void chopTree(Block block, Player player, boolean ConnectedBlocks, Location location, Material material, BlockData blockData) {
        // Return if player don't have Residence, Lands or GriefPrevention permission in this area
        if (!resCheck(player, location)) {
            return;
        }
        if (!landsCheck(player, location)) {
            return;
        }
        if (!gfCheck(player, location)) {
            return;
        }

        if (chopTreeInit(block, player, toolDamageDecrease)) return;

        // Async in Bukkit, but use sync method in Folia, because async system cause some issues for Folia.
        if (!isFolia() && chopTreeAsync) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                for (int yOffset = -1; yOffset <= 1; yOffset++) {
                    for (int xOffset = -1; xOffset <= 1; xOffset++) {
                        for (int zOffset = -1; zOffset <= 1; zOffset++) {
                            if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
                                continue;
                            }
                            Block relativeBlock = block.getRelative(xOffset, yOffset, zOffset);
                            if (stopChoppingIfDifferentTypes && notSameType(block.getType(), relativeBlock.getType())) {
                                continue;
                            }
                            // Check if the relative block is connected to the original block.
                            if (ConnectedBlocks && blockNotConnected(block, relativeBlock)) {
                                continue;
                            }
                            // CoreProtect logging
                            if (getServer().getPluginManager().getPlugin("CoreProtect") != null) {
                                CoreProtectAPI coiApi = new CoreProtectAPI();
                                Location relativeLocation = relativeBlock.getLocation();  // Use the relative block's location
                                coiApi.logRemoval(player.getName(), relativeLocation, material, blockData);
                            }

                            // Stop if no enough credits
                            if (getPlayerConfig(player.getUniqueId()).getDailyUses() >= maxUsesPerDay && !hasvipBlock(player, getPlayerConfig(player.getUniqueId()))) {
                                BukkitTinyTranslations.sendMessage(player, HIT_MAX_USAGE_MESSAGE);
                                return;
                            }
                            if (getPlayerConfig(player.getUniqueId()).getDailyBlocksBroken() >= maxBlocksPerDay && !hasvipBlock(player, getPlayerConfig(player.getUniqueId()))) {
                                BukkitTinyTranslations.sendMessage(player, HIT_MAX_BLOCK_MESSAGE);
                                return;
                            }

                            Bukkit.getScheduler().runTask(this,
                                    () -> chopTree(relativeBlock, player, ConnectedBlocks, location, material, blockData));
                        }
                    }
                }
            });
        } else {
            for (int yOffset = -1; yOffset <= 1; yOffset++) {
                for (int xOffset = -1; xOffset <= 1; xOffset++) {
                    for (int zOffset = -1; zOffset <= 1; zOffset++) {
                        if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
                            continue;
                        }
                        Block relativeBlock = block.getRelative(xOffset, yOffset, zOffset);
                        if (stopChoppingIfDifferentTypes && notSameType(block.getType(), relativeBlock.getType())) {
                            continue;
                        }
                        // Check if the relative block is connected to the original block.
                        if (ConnectedBlocks && blockNotConnected(block, relativeBlock)) {
                            continue;
                        }
                        // CoreProtect logging
                        if (getServer().getPluginManager().getPlugin("CoreProtect") != null) {
                            CoreProtectAPI coiApi = new CoreProtectAPI();
                            Location relativeLocation = relativeBlock.getLocation();  // Use the relative block's location
                            coiApi.logRemoval(player.getName(), relativeLocation, material, blockData);
                        }

                        // Stop if no enough credits
                        if (getPlayerConfig(player.getUniqueId()).getDailyUses() >= maxUsesPerDay && !hasvipBlock(player, getPlayerConfig(player.getUniqueId()))) {
                            BukkitTinyTranslations.sendMessage(player, HIT_MAX_USAGE_MESSAGE);
                            return;
                        }
                        if (getPlayerConfig(player.getUniqueId()).getDailyBlocksBroken() >= maxBlocksPerDay && !hasvipBlock(player, getPlayerConfig(player.getUniqueId()))) {
                            BukkitTinyTranslations.sendMessage(player, HIT_MAX_BLOCK_MESSAGE);
                            return;
                        }

                        chopTree(relativeBlock, player, ConnectedBlocks, location, material, blockData);
                    }
                }
            }
        }
    }

    private void setCooldown(Player player, UUID playerUUID) {
        if (player.hasPermission("autotreechop.vip")) {
            cooldowns.put(playerUUID, System.currentTimeMillis() + (vipCooldownTime * 1000L));
        } else  {
            cooldowns.put(playerUUID, System.currentTimeMillis() + (cooldownTime * 1000L));
        }
    }

    private boolean isInCooldown(UUID playerUUID) {
        Long cooldownEnd = cooldowns.get(playerUUID);
        if (cooldownEnd == null) {
            return false;
        }
        return System.currentTimeMillis() < cooldownEnd;
    }

    private long getRemainingCooldown(UUID playerUUID) {
        Long cooldownEnd = cooldowns.get(playerUUID);
        if (cooldownEnd == null) {
            return 0;
        }
        long remainingTime = cooldownEnd - System.currentTimeMillis();
        return Math.max(0, remainingTime / 1000);
    }

    // Check if player have Lands permission in this area
    // It will return true if player have permission, and vice versa.
    public boolean landsCheck(Player player, @NotNull Location location) {
        if (this.getServer().getPluginManager().getPlugin("Lands") == null) {
            return true;
        }
        if (location.getWorld() == null) {
            return false;
        }
        LandsIntegration landsapi = LandsIntegration.of(this);
        LandWorld world = landsapi.getWorld(location.getWorld());

        if (world != null) { // Lands is enabled in this world
            return world.hasFlag(player, location, null, me.angeschossen.lands.api.flags.Flags.BLOCK_BREAK, false);
        }

        return true;
    }

    public boolean gfCheck(Player player, Location location) {
        if (this.getServer().getPluginManager().getPlugin("GriefPrevention") == null) { return true; }

        if (GriefPrevention.instance.dataStore.getClaimAt(location, false, null) == null) { return true; }

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);

        if (claim.getOwnerID().equals(player.getUniqueId()) || player.hasPermission("catchball.op") || player.isOp()) { return true; }

        if (!claim.hasExplicitPermission(player, ClaimPermission.valueOf(griefPreventionFlag))) {
            BukkitTinyTranslations.sendMessage(player, noResidencePermissions);
            return false;
        }

        return true;
    }

    // Check if the two blocks are adjacent to each other.
    private boolean blockNotConnected(Block block1, Block block2) {
        if (block1.getX() == block2.getX() && block1.getY() == block2.getY() && Math.abs(block1.getZ() - block2.getZ()) == 1) {
            return false;
        }
        if (block1.getX() == block2.getX() && Math.abs(block1.getY() - block2.getY()) == 1 && block1.getZ() == block2.getZ()) {
            return false;
        }
        return Math.abs(block1.getX() - block2.getX()) != 1 || block1.getY() != block2.getY() || block1.getZ() != block2.getZ();
    }

    private boolean chopTreeInit(Block block, Player player, int damageToolInt) {
        UUID playerUUID = player.getUniqueId();
        PlayerConfig playerConfig = getPlayerConfig(playerUUID);
        if (checkedLocations.contains(block.getLocation())) {
            return true;
        }
        checkedLocations.add(block.getLocation());

        if (isLog(block.getType())) {
            block.breakNaturally();
        } else {
            return true;
        }

        playerConfig.incrementDailyBlocksBroken();
        if (toolDamage) {
            damageTool(player, damageToolInt);
        }
        return false;
    }

    // Check if player have Residence permission in this area
    // It will return true if player have permission, and vice versa.
    private boolean resCheck(Player player, Location location) {
        if (this.getServer().getPluginManager().getPlugin("Residence") == null) {
            return true;
        }

        if (ResidenceApi.getResidenceManager().getByLoc(location) == null) {
            return true;
        }

        ClaimedResidence residence = ResidenceApi.getResidenceManager().getByLoc(location);

        if (residence.getOwnerUUID().equals(player.getUniqueId()) || player.isOp() || player.hasPermission("catchball.op")) {
            return true;
        }

        if (!residence.getPermissions().playerHas(player, Flags.valueOf(residenceFlag.toLowerCase()), true)) {

            BukkitTinyTranslations.sendMessage(player, noResidencePermissions);

            return false;
        }
        return true;
    }

    // Add a new method to check if two block types are the same
    private boolean notSameType(Material type1, Material type2) {
        return type1 != type2;
    }


    private boolean isLog(Material material) {
        return logTypes.contains(material);
    }

    PlayerConfig getPlayerConfig(UUID playerUUID) {
        PlayerConfig playerConfig = playerConfigs.get(playerUUID);
        if (playerConfig == null) {
            playerConfig = new PlayerConfig(playerUUID, useMysql, hostname, database, port, username, password);
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