package org.milkteamc.autotreechop;

import cn.handyplus.lib.adapter.HandySchedulerUtil;
import com.bekvon.bukkit.residence.api.ResidenceApi;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.jeff_media.updatechecker.UpdateCheckSource;
import com.jeff_media.updatechecker.UpdateChecker;
import com.jeff_media.updatechecker.UserAgentBuilder;
import de.cubbossa.tinytranslations.Message;
import de.cubbossa.tinytranslations.MessageBuilder;
import de.cubbossa.tinytranslations.TinyTranslations;
import de.cubbossa.tinytranslations.Translator;
import de.cubbossa.tinytranslations.persistent.PropertiesMessageStorage;
import de.cubbossa.tinytranslations.persistent.PropertiesStyleStorage;
import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.land.LandWorld;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.kyori.adventure.platform.AudienceProvider;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;

import static cn.handyplus.lib.adapter.HandySchedulerUtil.isFolia;

public class AutoTreeChop extends JavaPlugin implements Listener, CommandExecutor {

    private Map<UUID, PlayerConfig> playerConfigs;
    private AutoTreeChopAPI api;
    public static final Message noResidencePermissions = new MessageBuilder("noResidencePermissions")
            .withDefault("<negative>You don't have permission to use AutoTreeChop at here.</negative>").build();

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
            .withDefault("<positive>You have used the AutoTreeChop {current_uses}/{max_uses} times today.</positive>").build();
    public static final Message BLOCKS_BROKEN_MESSAGE = new MessageBuilder("blocks-broken")
            .withDefault("<positive>You have broken {current_blocks}/{max_blocks} blocks today.</positive>").build();
    public static final Message ENABLED_BY_OTHER_MESSAGE = new MessageBuilder("enabledByOther")
            .withDefault("<positive>Auto tree chopping enabled by {player}.</positive>").build();
    public static final Message ENABLED_FOR_OTHER_MESSAGE = new MessageBuilder("enabledForOther")
            .withDefault("<positive>Auto tree chopping enabled for {player}</positive>").build();
    public static final Message DISABLED_BY_OTHER_MESSAGE = new MessageBuilder("disabledByOther")
            .withDefault("<negative>Auto tree chopping disabled by {player}.</negative>").build();
    public static final Message DISABLED_FOR_OTHER_MESSAGE = new MessageBuilder("disabledForOther")
            .withDefault("<negative>Auto tree chopping disabled for {player}</negative>").build();
    public static final Message CONSOLE_NAME = new MessageBuilder("consoleName")
            .withDefault("console").build();
    LandsIntegration landsapi;

    private boolean VisualEffect;
    private boolean toolDamage;

    private int maxUsesPerDay;
    private int maxBlocksPerDay;

    private boolean stopChoppingIfNotConnected;
    private boolean stopChoppingIfDifferentTypes;
    private String residenceFlag;

    private Locale locale;
    private AudienceProvider audienceProvider;
    private Translator translations;

    private static final String SPIGOT_RESOURCE_ID = "113071";

    CoreProtectAPI coiApi = getCoreProtect();

    public void sendMessage(CommandSender sender, ComponentLike message) {
        if (sender instanceof Player player) {
            audienceProvider.player(player.getUniqueId()).sendMessage(message);
            return;
        }
        audienceProvider.console().sendMessage(message);
    }

    private static final List<String> SUPPORTED_VERSIONS = Arrays.asList(
            "1.20.4-R0.1-SNAPSHOT",
            "1.20.3-R0.1-SNAPSHOT",
            "1.20.2-R0.1-SNAPSHOT",
            "1.20.1-R0.1-SNAPSHOT",
            "1.20-R0.1-SNAPSHOT",
            "1.19.4-R0.1-SNAPSHOT",
            "1.19.3-R0.1-SNAPSHOT",
            "1.19.2-R0.1-SNAPSHOT",
            "1.19.1-R0.1-SNAPSHOT",
            "1.19-R0.1-SNAPSHOT",
            "1.18.2-R0.1-SNAPSHOT",
            "1.18.1-R0.1-SNAPSHOT",
            "1.18-R0.1-SNAPSHOT",
            "1.17.1-R0.1-SNAPSHOT",
            "1.17-R0.1-SNAPSHOT"
    );

    @NotNull
    private static FileConfiguration getDefaultConfig() {
        FileConfiguration defaultConfig;

        defaultConfig = new YamlConfiguration();
        defaultConfig.set("visual-effect", true);
        defaultConfig.set("toolDamage", true);
        defaultConfig.set("max-uses-per-day", 50);
        defaultConfig.set("max-blocks-per-day", 500);
        defaultConfig.set("stopChoppingIfNotConnected", false);
        defaultConfig.set("stopChoppingIfDifferentTypes", false);
        defaultConfig.set("locale", Locale.ENGLISH);
        defaultConfig.set("residenceFlag", "build");
        return defaultConfig;
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
        Object locale = config.get("locale");
        residenceFlag = config.getString("residenceFlag");
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
                    sendMessage(player, USAGE_MESSAGE
                            .insertNumber("current_uses", playerConfig.getDailyUses())
                            .insertNumber("max_uses", maxUsesPerDay));
                    sendMessage(player, BLOCKS_BROKEN_MESSAGE
                            .insertNumber("current_blocks", playerConfig.getDailyBlocksBroken())
                            .insertNumber("max_blocks", maxBlocksPerDay));
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
                                sendMessage(player, ENABLED_FOR_OTHER_MESSAGE.insertString("player", targetPlayer.getName()));
                                sendMessage(targetPlayer, ENABLED_BY_OTHER_MESSAGE.insertString("player", player.getName()));
                            } else {
                                sendMessage(player, DISABLED_FOR_OTHER_MESSAGE.insertString("player", targetPlayer.getName()));
                                sendMessage(targetPlayer, DISABLED_BY_OTHER_MESSAGE.insertString("player", player.getName()));
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
                            sendMessage(sender, ENABLED_FOR_OTHER_MESSAGE.insertString("player", targetPlayer.getName()));
                            sendMessage(targetPlayer, ENABLED_BY_OTHER_MESSAGE.insertComponent("player", CONSOLE_NAME));
                        } else {
                            sendMessage(sender, DISABLED_FOR_OTHER_MESSAGE.insertString("player", targetPlayer.getName()));
                            sendMessage(targetPlayer, DISABLED_BY_OTHER_MESSAGE.insertComponent("player", CONSOLE_NAME));
                        }
                    } else {
                        getLogger().warning("Player not found: " + args[0]);
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
        if (!SUPPORTED_VERSIONS.contains(this.getServer().getBukkitVersion())) {
            getLogger().warning("Your Minecraft version is may have some issues, we only fully support "
                    + String.join(", ", SUPPORTED_VERSIONS));
            getLogger().warning("Report any issue to our GitHub: https://github.com/milkteamc/AutoTreeChop/issues");
        }

        org.milkteamc.autotreechop.Metrics metrics = new Metrics(this, 20053); //bstats

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("autotreechop").setExecutor(this);

        saveDefaultConfig();
        loadConfig();

        audienceProvider = BukkitAudiences.create(this);
        TinyTranslations.enable(new File(getDataFolder(), "/.."));
        translations = TinyTranslations.application("AutoTreeChop");
        // always use the configured locale, no matter what user.
        translations.setLocaleProvider(audience -> locale == null ? Locale.ENGLISH : locale);

        translations.setMessageStorage(new PropertiesMessageStorage(new File(getDataFolder(), "/lang/")));
        translations.setStyleStorage(new PropertiesStyleStorage(new File(getDataFolder(), "/lang/styles.properties")));

        // Register all messages from this class and save them into an en.properties and a de.properties.
        // If already exists, this will only write missing values into these files.
        translations.addMessages(TinyTranslations.messageFieldsFromClass(AutoTreeChop.class));
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

        if (isFolia()) {
            getLogger().warning("It seen you are using Folia, some function may not work.");
            foliaUpdateChecker();
        } else {
            spigotUpdateChecker();
        }
        api = new AutoTreeChopAPI(this);
        playerConfigs = new HashMap<>();

        if (this.getServer().getPluginManager().getPlugin("Lands") != null) {
            landsapi = LandsIntegration.of(this);
        }
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
        Location location = block.getLocation();
        BlockData blockData = block.getBlockData();

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

            chopTree(block, player, stopChoppingIfNotConnected, location, material, blockData);

            checkedLocations.clear();

            playerConfig.incrementDailyUses();
        }
    }

    private void spigotUpdateChecker() {
        new UpdateChecker(this, UpdateCheckSource.SPIGOT, SPIGOT_RESOURCE_ID) // You can also use Spiget instead of Spigot - Spiget's API is usually much faster up to date.
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
                this.getLogger().warning("You are using a beta version!");
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

    private void chopTree(Block block, Player player, boolean ConnectedBlocks, Location location, Material material, BlockData blockData) {
        // Return if player don't have Residence permission in this area
        if (!resCheck(player, location) || !landsCheck(player, location)) {
            return;
        }

        if (chopTreeInit(block, player)) return;

        // CoreProtect logging
        coiApi.logRemoval(String.valueOf(player), location, material, blockData);

        // Async in Bukkit, but use sync method in Folia, because async system cause some issues for Folia.
        if (!isFolia()) {
            HandySchedulerUtil.runTaskAsynchronously(() -> {
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

                            HandySchedulerUtil.runTask(() -> chopTree(relativeBlock, player, ConnectedBlocks, location, material, blockData));
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

                        chopTree(relativeBlock, player, ConnectedBlocks, location, material, blockData);
                    }
                }
            }
        }
    }

    // Check if player have Residence permission in this area
    // It will return true if player have permission, and vice versa.
    private boolean resCheck(Player player, Location location) {
        if (this.getServer().getPluginManager().getPlugin("Residence") == null) { return true; }

        if (ResidenceApi.getResidenceManager().getByLoc(location) == null) { return true; }

        ClaimedResidence residence = ResidenceApi.getResidenceManager().getByLoc(location);

        if (residence.getOwnerUUID().equals(player.getUniqueId()) || player.isOp() || player.hasPermission("catchball.op")) { return true; }

            if (!residence.getPermissions().playerHas(player, Flags.valueOf(residenceFlag.toLowerCase()) , true)) {

                sendMessage(player, noResidencePermissions);

                return false;
            }
            return true;
    }

    // Check if player have Lands permission in this area
    // It will return true if player have permission, and vice versa.
    public boolean landsCheck(Player player, Location location) {
        if (this.getServer().getPluginManager().getPlugin("Lands") == null) { return true; }
        LandWorld world = landsapi.getWorld(location.getWorld());

        if (world != null) { // Lands is enabled in this world
            if (world.hasFlag(player, location, null, me.angeschossen.lands.api.flags.Flags.BLOCK_BREAK, false)) {
                return true;
            } else {
                return false;
            }
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

    private boolean chopTreeInit(Block block, Player player) {
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
            damageTool(player, 1);
        }
        return false;
    }

    // Add a new method to check if two block types are the same
    private boolean notSameType(Material type1, Material type2) {
        return type1 != type2;
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

    private CoreProtectAPI getCoreProtect() {
        Plugin plugin = getServer().getPluginManager().getPlugin("CoreProtect");

        // Check that CoreProtect is loaded
        if (!(plugin instanceof CoreProtect)) {
            return null;
        }

        // Check that the API is enabled
        CoreProtectAPI CoreProtect = ((CoreProtect) plugin).getAPI();
        if (!CoreProtect.isEnabled()) {
            return null;
        }

        // Check that a compatible version of the API is loaded
        if (CoreProtect.APIVersion() < 9) {
            return null;
        }

        return CoreProtect;
    }

    public AutoTreeChopAPI getAPI() {
        return api;
    }
}