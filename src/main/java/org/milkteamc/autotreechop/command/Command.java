package org.milkteamc.autotreechop.command;

import de.cubbossa.tinytranslations.BukkitTinyTranslations;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.PlayerConfig;

import java.util.UUID;

import static org.milkteamc.autotreechop.AutoTreeChop.*;

public class Command implements CommandExecutor {

    private final AutoTreeChop plugin;
    private final Config config;

    public Command(AutoTreeChop plugin) {
        this.plugin = plugin;
        this.config = plugin.getPluginConfig();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command cmd, @NotNull String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("autotreechop") && !cmd.getName().equalsIgnoreCase("atc")) {
            return false;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("autotreechop.reload")) {
                config.load(); // Reload the config
                plugin.getSaplingManager().load();
                sender.sendMessage("Config reloaded successfully.");
                sender.sendMessage("Some features might need a fully restart to change properly!");
            } else {
                sendMessage(sender, NO_PERMISSION_MESSAGE);
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("about")) {
            sender.sendMessage("AutoTreeChop - " + plugin.getDescription().getVersion() + " is made by MilkTeaMC team and contributors");
            sender.sendMessage("This JAR and the source code is licensed under the GNU General Public License v3.0 (GPL-3.0)");
            sender.sendMessage("GitHub: https://github.com/milkteamc/autotreechop");
            sender.sendMessage("Discord: https://discord.gg/uQ4UXANnP2");
            sender.sendMessage("Modrinth: https://modrinth.com/plugin/autotreechop");
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
            PlayerConfig playerConfig = plugin.getPlayerConfig(player.getUniqueId());
            BukkitTinyTranslations.sendMessage(player, USAGE_MESSAGE
                    .insertNumber("current_uses", playerConfig.getDailyUses())
                    .insertNumber("max_uses", config.getMaxUsesPerDay()));
            BukkitTinyTranslations.sendMessage(player, BLOCKS_BROKEN_MESSAGE
                    .insertNumber("current_blocks", playerConfig.getDailyBlocksBroken())
                    .insertNumber("max_blocks", config.getMaxBlocksPerDay()));
        } else if (player.hasPermission("autotreechop.vip") && config.getLimitVipUsage()) {
            PlayerConfig playerConfig = plugin.getPlayerConfig(player.getUniqueId());
            BukkitTinyTranslations.sendMessage(player, USAGE_MESSAGE
                    .insertNumber("current_uses", playerConfig.getDailyUses())
                    .insertNumber("max_uses", config.getVipUsesPerDay()));
            BukkitTinyTranslations.sendMessage(player, BLOCKS_BROKEN_MESSAGE
                    .insertNumber("current_blocks", playerConfig.getDailyBlocksBroken())
                    .insertNumber("max_blocks", config.getVipBlocksPerDay()));
        } else if (player.hasPermission("autotreechop.vip") && !config.getLimitVipUsage()) {
            PlayerConfig playerConfig = plugin.getPlayerConfig(player.getUniqueId());
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
        PlayerConfig playerConfig = plugin.getPlayerConfig(targetUUID);
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
        PlayerConfig playerConfig = plugin.getPlayerConfig(playerUUID);
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
        de.cubbossa.tinytranslations.Message message = autoTreeChopEnabled
                ? ENABLED_BY_OTHER_MESSAGE.insertString("player", sender.getName())
                : DISABLED_BY_OTHER_MESSAGE.insertString("player", sender.getName());

        // Use parallelStream for better performance
        Bukkit.getOnlinePlayers().parallelStream().forEach(onlinePlayer -> {
            UUID playerUUID = onlinePlayer.getUniqueId();
            PlayerConfig playerConfig = plugin.getPlayerConfig(playerUUID);
            playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);
            sendMessage(onlinePlayer, message);
        });
    }
}
