package org.milkteamc.autotreechop;

import com.jeff_media.updatechecker.UpdateCheckSource;
import com.jeff_media.updatechecker.UpdateChecker;
import com.jeff_media.updatechecker.UserAgentBuilder;
import de.cubbossa.tinytranslations.*;
import de.cubbossa.tinytranslations.libs.kyori.adventure.text.ComponentLike;
import de.cubbossa.tinytranslations.storage.properties.PropertiesMessageStorage;
import de.cubbossa.tinytranslations.storage.properties.PropertiesStyleStorage;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.milkteamc.autotreechop.hooks.GriefPreventionHook;
import org.milkteamc.autotreechop.hooks.LandsHook;
import org.milkteamc.autotreechop.hooks.ResidenceHook;
import org.milkteamc.autotreechop.hooks.WorldGuardHook;

import java.io.File;
import java.util.*;

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
            "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
            "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
            "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
            "1.18.2", "1.18.1", "1.18",
            "1.17.1", "1.17"
    );

    private Config config; // Instance of your Config class
    private AutoTreeChopAPI autoTreeChopAPI;
    private Map<UUID, PlayerConfig> playerConfigs;
    private final Set<Location> checkedLocations = new HashSet<>();
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final Set<Location> processingLocations = new HashSet<>();
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

    public static void sendMessage(CommandSender sender, ComponentLike message) {
        BukkitTinyTranslations.sendMessageIfNotEmpty(sender, message);
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

    @Override
    public void onEnable() {
        // Initialize Config
        config = new Config(this);

        // Bukkit version checker
        // Put your version check *after* loading the config, in case you add version-specific settings.
        if (bukkitVersion.length() > 14) {
            bukkitVersion = bukkitVersion.substring(0, bukkitVersion.length() - 14);
            if (!SUPPORTED_VERSIONS.contains(bukkitVersion)) {
                getLogger().warning("Your Minecraft version didn't fully tested yet.");
                getLogger().warning("IF you have any issues, feel free to report it at our GitHub: https://github.com/milkteamc/AutoTreeChop/issues");
            }
        }

        metrics = new Metrics(this, 20053); //bstats
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("autotreechop").setExecutor(this);
        getCommand("atc").setExecutor(this);
        //saveDefaultConfig(); No longer needed, handled in Config class.
        //loadConfig();  No longer needed, handled in Config class constructor.

        translations = BukkitTinyTranslations.application(this);
        translations.setMessageStorage(new PropertiesMessageStorage(new File(getDataFolder(), "/lang/")));
        translations.setStyleStorage(new PropertiesStyleStorage(new File(getDataFolder(), "/lang/styles.properties")));
        translations.addMessages(TinyTranslations.messageFieldsFromClass(AutoTreeChop.class));

        loadLocale(); //Still needs to be called to *use* the locale.

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

        autoTreeChopAPI = new AutoTreeChopAPI(this);
        playerConfigs = new HashMap<>();
        initializeHooks(); // Initialize protection plugin hooks
    }


    private void initializeHooks() {
        // Residence hook initialization
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
        // GriefPrevention hook initialization
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
        // Lands hook initialization
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
        // Initialize WorldGuard support
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                worldGuardHook = new WorldGuardHook();
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
        saveResourceIfNotExists("lang/zh.properties");
        saveResourceIfNotExists("lang/zh-CN.properties");
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
        translations.close();
        metrics.shutdown();
    }


    // VIP limit checker
    private boolean hasvipUses(Player player, PlayerConfig playerConfig) {
        if (!config.getLimitVipUsage()) return player.hasPermission("autotreechop.vip");
        if (player.hasPermission("autotreechop.vip")) return playerConfig.getDailyUses() <= config.getVipUsesPerDay();
        return false;
    }

    private boolean hasvipBlock(Player player, PlayerConfig playerConfig) {
        if (!config.getLimitVipUsage()) return player.hasPermission("autotreechop.vip");
        if (player.hasPermission("autotreechop.vip"))
            return playerConfig.getDailyBlocksBroken() <= config.getVipBlocksPerDay();
        return false;
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
                config.load(); // Reload the config
                loadLocale();   //And reload the locale.
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
                    .insertNumber("max_uses", config.getMaxUsesPerDay()));
            BukkitTinyTranslations.sendMessage(player, BLOCKS_BROKEN_MESSAGE
                    .insertNumber("current_blocks", playerConfig.getDailyBlocksBroken())
                    .insertNumber("max_blocks", config.getMaxBlocksPerDay()));
        } else if (player.hasPermission("autotreechop.vip") && config.getLimitVipUsage()) {
            PlayerConfig playerConfig = getPlayerConfig(player.getUniqueId());
            BukkitTinyTranslations.sendMessage(player, USAGE_MESSAGE
                    .insertNumber("current_uses", playerConfig.getDailyUses())
                    .insertNumber("max_uses", config.getVipUsesPerDay()));
            BukkitTinyTranslations.sendMessage(player, BLOCKS_BROKEN_MESSAGE
                    .insertNumber("current_blocks", playerConfig.getDailyBlocksBroken())
                    .insertNumber("max_blocks", config.getVipBlocksPerDay()));
        } else if (player.hasPermission("autotreechop.vip") && !config.getLimitVipUsage()) {
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

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        PlayerConfig playerConfig = getPlayerConfig(playerUUID);
        Block block = event.getBlock();

        // Skip if this block is already being processed
        if (processingLocations.contains(block.getLocation())) {
            return;
        }

        if (isInCooldown(playerUUID)) {
            sendMessage(player, STILL_IN_COOLDOWN_MESSAGE
                    .insertNumber("cooldown_time", getRemainingCooldown(playerUUID))
            );
            event.setCancelled(true);
            return;
        }

        Material material = block.getType();
        Location location = block.getLocation();
        BlockData blockData = block.getBlockData();

        if (playerConfig.isAutoTreeChopEnabled() && isLog(material)) {
            if (!hasvipBlock(player, playerConfig) && playerConfig.getDailyBlocksBroken() >= config.getMaxBlocksPerDay()) {
                sendMaxBlockLimitReachedMessage(player, block);
                event.setCancelled(true);
                return;
            }
            if (!hasvipUses(player, playerConfig) && playerConfig.getDailyUses() >= config.getMaxUsesPerDay()) {
                BukkitTinyTranslations.sendMessage(player, HIT_MAX_USAGE_MESSAGE);
                return;
            }

            if (config.isVisualEffect()) {  // Use the getter from the Config object
                showChopEffect(player, block);
            }

            event.setCancelled(true);
            checkedLocations.clear();
            chopTree(block, player, config.isStopChoppingIfNotConnected(), location, material, blockData); // Pass config values
            checkedLocations.clear();
            playerConfig.incrementDailyUses();
            setCooldown(player, playerUUID); // Pass config values
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
                player.getInventory().setItemInMainHand(null); // Remove the item if it breaks
            } else {
                tool.setDurability((short) newDurability);
            }
        }
    }

    private void chopTree(Block block, Player player, boolean ConnectedBlocks, Location location, Material material, BlockData blockData) {
        // Permission checks
        if (!resCheck(player, location) || !landsCheck(player, location) ||
                !gfCheck(player, location) || !wgCheck(player, location)) {
            return;
        }
        // Skip if already checked or being processed
        if (checkedLocations.contains(block.getLocation()) ||
                processingLocations.contains(block.getLocation())) {
            return;
        }

        checkedLocations.add(block.getLocation());

        if (!isLog(block.getType())) {
            return;
        }
        // Add to processing set to prevent recursion
        processingLocations.add(block.getLocation());

        // Call BlockBreakEvent for this block
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(breakEvent);

        if (!breakEvent.isCancelled()) {
            // Break the block and update player stats
            block.breakNaturally();
            getPlayerConfig(player.getUniqueId()).incrementDailyBlocksBroken();
            if (config.isToolDamage()) {
                damageTool(player, config.getToolDamageDecrease());
            }

            // Process adjacent blocks
            Runnable task = () -> {
                for (int yOffset = -1; yOffset <= 1; yOffset++) {
                    for (int xOffset = -1; xOffset <= 1; xOffset++) {
                        for (int zOffset = -1; zOffset <= 1; zOffset++) {
                            if (xOffset == 0 && yOffset == 0 && zOffset == 0) continue;

                            Block relativeBlock = block.getRelative(xOffset, yOffset, zOffset);

                            if (config.isStopChoppingIfDifferentTypes() && notSameType(block.getType(), relativeBlock.getType())) {
                                continue;
                            }
                            if (ConnectedBlocks && blockNotConnected(block, relativeBlock)) {
                                continue;
                            }

                            // Check limits before processing next block
                            PlayerConfig configPlayer = getPlayerConfig(player.getUniqueId());
                            if (configPlayer.getDailyUses() >= config.getMaxUsesPerDay() && !hasvipBlock(player, configPlayer)) {
                                BukkitTinyTranslations.sendMessage(player, HIT_MAX_USAGE_MESSAGE);
                                return;
                            }
                            if (configPlayer.getDailyBlocksBroken() >= config.getMaxBlocksPerDay() && !hasvipBlock(player, configPlayer)) {
                                BukkitTinyTranslations.sendMessage(player, HIT_MAX_BLOCK_MESSAGE);
                                return;
                            }

                            // Schedule next block processing
                            if (isFolia()) {
                                this.getServer().getRegionScheduler().run(this, relativeBlock.getLocation(),
                                        (task2) -> chopTree(relativeBlock, player, ConnectedBlocks, location, material, blockData));
                            } else {
                                if (config.isChopTreeAsync()) {
                                    Bukkit.getScheduler().runTaskAsynchronously(this, () ->
                                            Bukkit.getScheduler().runTask(this, () ->
                                                    chopTree(relativeBlock, player, ConnectedBlocks, location, material, blockData)));
                                } else {
                                    Bukkit.getScheduler().runTask(this, () ->
                                            chopTree(relativeBlock, player, ConnectedBlocks, location, material, blockData));
                                }
                            }
                        }
                    }
                }
                // Remove from processing set after all adjacent blocks are handled
                processingLocations.remove(block.getLocation());
            };

            // Execute the task based on configuration and environment
            if (!isFolia() && config.isChopTreeAsync()) {
                Bukkit.getScheduler().runTaskAsynchronously(this, task);
            } else {
                task.run();
            }
        } else {
            processingLocations.remove(block.getLocation());
        }
    }

    private void setCooldown(Player player, UUID playerUUID) {
        if (player.hasPermission("autotreechop.vip")) {
            cooldowns.put(playerUUID, System.currentTimeMillis() + (config.getVipCooldownTime() * 1000L));
        } else {
            cooldowns.put(playerUUID, System.currentTimeMillis() + (config.getCooldownTime() * 1000L));
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
        return !landsEnabled || landsHook.checkBuild(player, location);
    }

    public boolean wgCheck(Player player, Location location) {
        if (!worldGuardEnabled) {
            return true;
        }
        return worldGuardHook.checkBuild(player, location);
    }

    public boolean gfCheck(Player player, Location location) {
        return !griefPreventionEnabled || griefPreventionHook.checkBuild(player, location);
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

    // Check if player have Residence permission in this area
    // It will return true if player have permission, and vice versa.
    private boolean resCheck(Player player, Location location) {
        return !residenceEnabled || residenceHook.checkBuild(player, location);
    }

    // Add a new method to check if two block types are the same
    private boolean notSameType(Material type1, Material type2) {
        return type1 != type2;
    }

    private boolean isLog(Material material) {
        return config.getLogTypes().contains(material);  // Use the getter
    }

    PlayerConfig getPlayerConfig(UUID playerUUID) {
        PlayerConfig playerConfig = playerConfigs.get(playerUUID);
        if (playerConfig == null) {
            playerConfig = new PlayerConfig(playerUUID, config.isUseMysql(), config.getHostname(), config.getDatabase(), config.getPort(), config.getUsername(), config.getPassword());
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

    public AutoTreeChopAPI getAutoTreeChopAPI() {
        return autoTreeChopAPI;
    }

    // Add a getter for the Config instance
    public Config getPluginConfig() {
        return config;
    }
}