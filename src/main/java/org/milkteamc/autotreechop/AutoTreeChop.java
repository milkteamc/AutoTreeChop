package org.milkteamc.autotreechop;

import com.jeff_media.updatechecker.UpdateCheckSource;
import com.jeff_media.updatechecker.UpdateChecker;
import com.jeff_media.updatechecker.UserAgentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
import java.time.LocalDate;
import java.util.*;

public class AutoTreeChop extends JavaPlugin implements Listener, CommandExecutor {

    private Map<UUID, PlayerConfig> playerConfigs;
    private String enabledMessage;
    private String disabledMessage;
    private String noPermissionMessage;
    private String hitmaxusageMessage;
    private String hitmaxblockMessage;
    private String usageMessage;
    private String blocksBrokenMessage;
    private String enabledByOtherMessage;
    private String disabledByOtherMessage;
    private String consoleName;
    private boolean VisualEffect;
    private boolean toolDamage;

    private int maxUsesPerDay;
    private int maxBlocksPerDay;

    private boolean stopChoppingIfNotConnected;
    private boolean stopChoppingIfDifferentTypes;

    private static final String SPIGOT_RESOURCE_ID = "20053";

    @Override
    public void onEnable() {
        org.milkteamc.autotreechop.Metrics metrics = new Metrics(this, 20053); //bstats

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("autotreechop").setExecutor(this);

        saveDefaultConfig();
        loadConfig();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new AutoTreeChopExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion for AutoTreeChop has been registered.");
        } else {
            getLogger().warning("PlaceholderAPI not found. Placeholder expansion for AutoTreeChop will not work.");
        }
        catch (Exception e) {
            return false;
        }
        return true;
    }

    private FileConfiguration loadConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        FileConfiguration defaultConfig;

        defaultConfig = new YamlConfiguration();
        defaultConfig.set("messages.enabled", "¡±aAuto tree chopping enabled.");
        defaultConfig.set("messages.disabled", "¡±cAuto tree chopping disabled.");
        defaultConfig.set("messages.no-permission", "¡±cYou don't have permission to use this command.");
        defaultConfig.set("messages.hitmaxusage", "¡±cYou've reached the daily usage limit.");
        defaultConfig.set("messages.hitmaxblock", "¡±cYou have reached your daily block breaking limit.");
        defaultConfig.set("messages.usage", "¡±aYou have used the AutoTreeChop {current_uses}/{max_uses} times today.");
        defaultConfig.set("messages.blocks-broken", "¡±aYou have broken {current_blocks}/{max_blocks} blocks today.");
        defaultConfig.set("messages.enabledByOther", "¡±aAuto tree chopping enabled by {player}.");
        defaultConfig.set("messages.disabledByOther", "¡±cAuto tree chopping disabled by {player}.");
        defaultConfig.set("messages.consoleName", "console");
        defaultConfig.set("visual-effect", true);
        defaultConfig.set("toolDamage", true);
        defaultConfig.set("max-uses-per-day", 50);
        defaultConfig.set("max-blocks-per-day", 500);
        defaultConfig.set("stopChoppingIfNotConnected", false);
        defaultConfig.set("stopChoppingIfDifferentTypes", false);

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

        enabledMessage = config.getString("messages.enabled");
        disabledMessage = config.getString("messages.disabled");
        noPermissionMessage = config.getString("messages.no-permission");
        hitmaxusageMessage = config.getString("messages.hitmaxusage");
        hitmaxblockMessage = config.getString("messages.hitmaxblock");
        usageMessage = config.getString("messages.usage");
        blocksBrokenMessage = config.getString("messages.blocks-broken");
        enabledByOtherMessage = config.getString("messages.enabledByOther");
        disabledByOtherMessage = config.getString("messages.disabledByOther");
        consoleName = config.getString("messages.consoleName");
        VisualEffect = config.getBoolean("visual-effect");
        toolDamage = config.getBoolean("toolDamage");
        maxUsesPerDay = config.getInt("max-uses-per-day");
        maxBlocksPerDay = config.getInt("max-blocks-per-day");
        stopChoppingIfNotConnected = config.getBoolean("stopChoppingIfNotConnected", false);
        stopChoppingIfDifferentTypes = config.getBoolean("stopChoppingIfDifferentTypes", false);

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
                    player.sendMessage(noPermissionMessage);
                    return true;
                }

                // Check if the user provided a player name
                if (args.length > 1) {
                    // Get the target player
                    Player targetPlayer = Bukkit.getPlayer(args[1]);

                    // Check if the target player is online
                    if (targetPlayer != null) {
                        // Check if the sender has the required permission to toggle other players' state
                        if (player.hasPermission("autotreechop.other") || player.hasPermission("autotreechop.op")) {
                            UUID targetUUID = targetPlayer.getUniqueId();
                            playerConfig = getPlayerConfig(targetUUID);

                            boolean autoTreeChopEnabled = !playerConfig.isAutoTreeChopEnabled();
                            playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);

                            if (autoTreeChopEnabled) {
                                String enabledByOtherMsg = enabledByOtherMessage.replace("{player}", player.getName());

                                player.sendMessage("Auto tree chopping enabled for " + targetPlayer.getName());
                                targetPlayer.sendMessage(enabledByOtherMsg);
                            } else {
                                String disabledByOtherMsg = disabledByOtherMessage.replace("{player}", player.getName());

                                player.sendMessage("Auto tree chopping disabled for " + targetPlayer.getName());
                                targetPlayer.sendMessage(disabledByOtherMsg);
                            }
                        } else {
                            player.sendMessage(noPermissionMessage);
                        }
                    } else {
                        player.sendMessage("Player not found: " + args[1]);
                    }
                    return true;
                }

                // Inside the onCommand method
                if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                    if (sender.hasPermission("autotreechop.reload")) {
                        loadConfig();
                        sender.sendMessage("Config reloaded successfully.");
                    } else {
                        sender.sendMessage(noPermissionMessage);
                    }
                    return true;
                }


                // If the user provided "usage" as an argument
                if (args.length > 0 && args[0].equalsIgnoreCase("usage")) {
                    playerConfig = getPlayerConfig(player.getUniqueId()); // Get playerConfig for sender
                    String usageMsg = usageMessage.replace("{current_uses}", String.valueOf(playerConfig.getDailyUses()))
                            .replace("{max_uses}", String.valueOf(maxUsesPerDay));
                    player.sendMessage(usageMsg);

                    String blocksMsg = blocksBrokenMessage.replace("{current_blocks}", String.valueOf(playerConfig.getDailyBlocksBroken()))
                            .replace("{max_blocks}", String.valueOf(maxBlocksPerDay));
                    player.sendMessage(blocksMsg);
                    return true;
                } else {
                    // Toggle the state for the sender
                    playerConfig = getPlayerConfig(player.getUniqueId()); // Get playerConfig for sender
                    boolean autoTreeChopEnabled = !playerConfig.isAutoTreeChopEnabled();
                    playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);

                    if (autoTreeChopEnabled) {
                        player.sendMessage(enabledMessage);
                    } else {
                        player.sendMessage(disabledMessage);
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
                                String enabledByOtherMsg = enabledByOtherMessage.replace("{player}", consoleName);

                                getLogger().info("Auto tree chopping enabled for " + targetPlayer.getName());
                                targetPlayer.sendMessage(enabledByOtherMsg);
                            } else {
                                String disabledByOtherMsg = disabledByOtherMessage.replace("{player}", consoleName);

                                getLogger().info("Auto tree chopping disabled for " + targetPlayer.getName());
                                targetPlayer.sendMessage(disabledByOtherMsg);
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

            if (!player.hasPermission("autotreechop.vip") && playerConfig.dailyUses >= maxUsesPerDay) {
                player.sendMessage(hitmaxusageMessage);
            }

            if (VisualEffect) {
                showChopEffect(player, block);
            }

            event.setCancelled(true);
            checkedLocations.clear();
            if (stopChoppingIfNotConnected) {
                chopTree(block, player);
            } else {
                chopTreeConnectedBlocks(block, player);
            }
            checkedLocations.clear();
            chopTree(block, player);

            addItemToInventoryOrDrop(player, material);

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
        player.sendMessage(hitmaxblockMessage);
        player.getWorld().spawnParticle(org.bukkit.Particle.REDSTONE, block.getLocation().add(0.5, 0.5, 0.5), 50, 0.5, 0.5, 0.5, 0);
    }

    // Shows a green particle effect indicating the block has been chopped
    private void showChopEffect(Player player, Block block) {
        player.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, block.getLocation().add(0.5, 0.5, 0.5), 50, 0.5, 0.5, 0.5, 0);
    }

    // Adds the item to the player's inventory if there's space, otherwise drops it in the world
    private void addItemToInventoryOrDrop(Player player, Material material) {
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), new ItemStack(material));
        } else {
            player.getInventory().addItem(new ItemStack(material));
        }
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
                    chopTree(relativeBlock, player);
                }
            }
        }
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
                    chopTreeConnectedBlocks(relativeBlock, player);
                }
            }
        }
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

    private PlayerConfig getPlayerConfig(UUID playerUUID) {
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


    private class PlayerConfig {
        private final File configFile;
        private final FileConfiguration config;

        private boolean autoTreeChopEnabled;
        private int dailyUses;
        private int dailyBlocksBroken;
        private LocalDate lastUseDate;

        public PlayerConfig(UUID playerUUID) {
            this.configFile = new File(getDataFolder() + "/cache", playerUUID.toString() + ".yml");
            this.config = YamlConfiguration.loadConfiguration(configFile);
            this.autoTreeChopEnabled = false;
            this.dailyUses = 0;
            this.dailyBlocksBroken = 0;
            this.lastUseDate = LocalDate.now();
            loadConfig();
            saveConfig();
        }

        private void loadConfig() {
            if (configFile.exists()) {
                autoTreeChopEnabled = config.getBoolean("autoTreeChopEnabled");
                dailyUses = config.getInt("dailyUses");
                dailyBlocksBroken = config.getInt("dailyBlocksBroken", 0);
                lastUseDate = LocalDate.parse(Objects.requireNonNull(config.getString("lastUseDate")));
            } else {
                config.set("autoTreeChopEnabled", autoTreeChopEnabled);
                config.set("dailyUses", dailyUses);
                config.set("dailyBlocksBroken", dailyBlocksBroken);
                String lastUseDateString = config.getString("lastUseDate");
                if (lastUseDateString != null) {
                    lastUseDate = LocalDate.parse(lastUseDateString);
                } else {
                    lastUseDate = LocalDate.now();
                    config.set("lastUseDate", lastUseDate.toString());
                    saveConfig();
                }
                saveConfig();
            }
        }

        private void saveConfig() {
            try {
                config.save(configFile);
            } catch (IOException e) {
                getLogger().warning("An error occurred:" + e);
            }
        }

        public boolean isAutoTreeChopEnabled() {
            return autoTreeChopEnabled;
        }

        public void setAutoTreeChopEnabled(boolean enabled) {
            this.autoTreeChopEnabled = enabled;
            config.set("autoTreeChopEnabled", enabled);
            saveConfig();
        }

        public int getDailyUses() {
            if (!lastUseDate.equals(LocalDate.now())) {
                dailyUses = 0;
                lastUseDate = LocalDate.now();
                config.set("dailyUses", dailyUses);
                config.set("lastUseDate", lastUseDate.toString());
                saveConfig();
            }
            return dailyUses;
        }

        public void incrementDailyUses() {
            if (!lastUseDate.equals(LocalDate.now())) {
                dailyUses = 0;
                lastUseDate = LocalDate.now();
            }
            dailyUses++;
            config.set("dailyUses", dailyUses);
            saveConfig();
        }
        public int getDailyBlocksBroken() {
            if (!lastUseDate.equals(LocalDate.now())) {
                dailyBlocksBroken = 0;
                lastUseDate = LocalDate.now();
                config.set("dailyBlocksBroken", dailyBlocksBroken);
                config.set("lastUseDate", lastUseDate.toString());
                saveConfig();
            }
            return dailyBlocksBroken;
        }

        public void incrementDailyBlocksBroken() {
            if (!lastUseDate.equals(LocalDate.now())) {
                dailyBlocksBroken = 0;
                lastUseDate = LocalDate.now();
            }
            dailyBlocksBroken++;
            config.set("dailyBlocksBroken", dailyBlocksBroken);
            saveConfig();
        }

    }
}