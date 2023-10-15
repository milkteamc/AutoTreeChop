package org.milkteamc.autotreechop;

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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
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
    private int maxUsesPerDay;
    private int maxBlocksPerDay;

    @Override
    public void onEnable() {
        org.milkteamc.autotreechop.Metrics metrics = new Metrics(this, 20053); //bstats

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("autotreechop").setExecutor(this);

        loadConfig();

        playerConfigs = new HashMap<>();
    }

    private void loadConfig() {
        File configFile = new File(getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            try {
                if (!configFile.getParentFile().exists()) {
                    configFile.getParentFile().mkdirs();
                }
                configFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return; // If there's an error creating the file, exit the method.
            }
        }

        Map<String, String> configWithComments = new LinkedHashMap<>();
        configWithComments.put("# AutoTreeChop by Maoyue", "");
        configWithComments.put("# Discord support server: https://discord.gg/uQ4UXANnP2", "");
        configWithComments.put("# Spigot Page: https://www.spigotmc.org/resources/113071", "");
        configWithComments.put("", ""); // Empty line
        configWithComments.put("# The number of times non-VIP players can chop down trees per day,", "");
        configWithComments.put("# you can give everyone \"autotreechop.vip\" permission to disable it.", "");
        configWithComments.put("max-uses-per-day", "50");
        configWithComments.put("# Tree blocks that non-VIP players can chop down every day,", "");
        configWithComments.put("# you can give everyone \"autotreechop.vip\" permission to disable it.", "");
        configWithComments.put("max-blocks-per-day", "500");
        configWithComments.put("", ""); // Empty line
        configWithComments.put("# Plugin message", "");
        configWithComments.put("messages:", "");
        configWithComments.put("  enabled", "¡±aAuto tree chopping enabled.");
        configWithComments.put("  disabled", "¡±cAuto tree chopping disabled.");
        configWithComments.put("  no-permission", "¡±cYou don't have permission to use this command.");
        configWithComments.put("  hitmaxusage", "¡±cYou've reached the daily usage limit.");
        configWithComments.put("  hitmaxblock", "¡±cYou have reached your daily block breaking limit.");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            for (Map.Entry<String, String> entry : configWithComments.entrySet()) {
                writer.write(entry.getKey() + (entry.getValue().isEmpty() ? "" : ": " + entry.getValue()));
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        enabledMessage = config.getString("messages.enabled");
        disabledMessage = config.getString("messages.disabled");
        noPermissionMessage = config.getString("messages.no-permission");
        hitmaxusageMessage = config.getString("messages.hitmaxusage");
        hitmaxblockMessage = config.getString("messages.hitmaxblock");
        maxUsesPerDay = config.getInt("max-uses-per-day");
        maxBlocksPerDay = config.getInt("max-blocks-per-day");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("autotreechop")) {
            if (sender instanceof Player player) {

                if (!player.hasPermission("autotreechop.use")) {
                    player.sendMessage(noPermissionMessage);
                    return true;
                }

                UUID playerUUID = player.getUniqueId();
                PlayerConfig playerConfig = getPlayerConfig(playerUUID);

                if (!player.hasPermission("autotreechop.vip") && playerConfig.getDailyUses() >= maxUsesPerDay) {
                    player.sendMessage(hitmaxusageMessage);
                    return true;
                }

                boolean autoTreeChopEnabled = !playerConfig.isAutoTreeChopEnabled();
                playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);

                if (autoTreeChopEnabled) {
                    player.sendMessage(enabledMessage);
                } else {
                    player.sendMessage(disabledMessage);
                }
            } else {
                sender.sendMessage("Only player can use this command.");
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
                player.sendMessage(hitmaxblockMessage);
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);
            checkedLocations.clear();
            chopTree(block);

            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItem(player.getLocation(), new ItemStack(material));
            } else {
                player.getInventory().addItem(new ItemStack(material));
            }

            playerConfig.incrementDailyUses();
            playerConfig.incrementDailyBlocksBroken();
        }
    }

    private Set<Location> checkedLocations = new HashSet<>();

    private void chopTree(Block block) {
        if (checkedLocations.contains(block.getLocation())) {
            return;
        }
        checkedLocations.add(block.getLocation());

        if (isLog(block.getType())) {
            block.breakNaturally();
        } else {
            return;
        }

        for (int yOffset = -1; yOffset <= 1; yOffset++) {
            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
                        continue;
                    }
                    Block relativeBlock = block.getRelative(xOffset, yOffset, zOffset);
                    chopTree(relativeBlock);
                }
            }
        }
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
                e.printStackTrace();
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