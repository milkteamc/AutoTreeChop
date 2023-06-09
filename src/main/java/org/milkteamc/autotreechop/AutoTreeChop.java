package org.milkteamc.autotreechop;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AutoTreeChop extends JavaPlugin implements Listener, CommandExecutor {

    private Map<UUID, PlayerConfig> playerConfigs;
    private String enabledMessage;
    private String disabledMessage;
    private String noPermissionMessage;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("autotreechop").setExecutor(this);

        loadConfig();

        playerConfigs = new HashMap<>();
    }

    private void loadConfig() {
        getConfig().addDefault("messages.enabled", "§a已開啟自動砍樹。");
        getConfig().addDefault("messages.disabled", "§c已關閉自動砍樹。");
        getConfig().addDefault("messages.no-permission", "§c你沒有權限使用此指令。");
        getConfig().options().copyDefaults(true);
        saveConfig();

        FileConfiguration config = getConfig();
        enabledMessage = config.getString("messages.enabled");
        disabledMessage = config.getString("messages.disabled");
        noPermissionMessage = config.getString("messages.no-permission");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("autotreechop")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                if (!player.hasPermission("autotreechop.use")) {
                    player.sendMessage(noPermissionMessage);
                    return true;
                }

                UUID playerUUID = player.getUniqueId();
                PlayerConfig playerConfig = getPlayerConfig(playerUUID);
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

        if (playerConfig.isAutoTreeChopEnabled()) {
            Block block = event.getBlock();
            Material material = block.getType();

            if (isLog(material)) {
                event.setCancelled(true);
                chopTree(block);

                if (player.getInventory().firstEmpty() == -1) {
                    player.getWorld().dropItem(player.getLocation(), new ItemStack(material));
                } else {
                    player.getInventory().addItem(new ItemStack(material));
                }
            }
        }
    }

    private void chopTree(Block block) {
        Block aboveBlock = block.getRelative(0, 1, 0);
        if (isLog(aboveBlock.getType())) {
            aboveBlock.breakNaturally();
            chopTree(aboveBlock);
        }

        block.breakNaturally();

        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int zOffset = -1; zOffset <= 1; zOffset++) {
                if (xOffset == 0 && zOffset == 0) {
                    continue;
                }
                Block relativeBlock = block.getRelative(xOffset, 0, zOffset);
                if (isLog(relativeBlock.getType())) {
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
        private final UUID playerUUID;
        private final File configFile;
        private final FileConfiguration config;

        private boolean autoTreeChopEnabled;

        public PlayerConfig(UUID playerUUID) {
            this.playerUUID = playerUUID;
            this.configFile = new File(getDataFolder() + "/cache", playerUUID.toString() + ".yml");
            this.config = YamlConfiguration.loadConfiguration(configFile);
            this.autoTreeChopEnabled = false;
            loadConfig();
            this.autoTreeChopEnabled = false;
            saveConfig();
        }

        private void loadConfig() {
            if (configFile.exists()) {
                autoTreeChopEnabled = config.getBoolean("autoTreeChopEnabled");
            } else {
                config.set("autoTreeChopEnabled", autoTreeChopEnabled);
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
    }
}