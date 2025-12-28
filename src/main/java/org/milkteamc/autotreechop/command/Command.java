package org.milkteamc.autotreechop.command;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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

                // Reload translations
                plugin.getTranslationManager().reload(
                        config.getLocale() == null ? java.util.Locale.getDefault() : config.getLocale(),
                        config.isUseClientLocale()
                );

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
                sendMessage(sender, ENABLED_FOR_OTHER_MESSAGE,
                        Placeholder.parsed("player", "everyone"));
            } else {
                sendMessage(sender, NO_PERMISSION_MESSAGE);
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("disable-all")) {
            if (sender.hasPermission("autotreechop.other") || sender.hasPermission("autotreechop.op")) {
                toggleAutoTreeChopForAll(sender, false);
                sendMessage(sender, DISABLED_FOR_OTHER_MESSAGE,
                        Placeholder.parsed("player", "everyone"));
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

        if (!config.getCommandToggle()) {
            sendMessage(player, NO_PERMISSION_MESSAGE);
            return true;
        }

        toggleAutoTreeChop(player, player.getUniqueId());
        return true;
    }

    private void handleUsageCommand(Player player) {
        PlayerConfig playerConfig = plugin.getPlayerConfig(player.getUniqueId());

        if (!player.hasPermission("autotreechop.vip")) {
            sendMessage(player, USAGE_MESSAGE,
                    Placeholder.parsed("current_uses", String.valueOf(playerConfig.getDailyUses())),
                    Placeholder.parsed("max_uses", String.valueOf(config.getMaxUsesPerDay()))
            );
            sendMessage(player, BLOCKS_BROKEN_MESSAGE,
                    Placeholder.parsed("current_blocks", String.valueOf(playerConfig.getDailyBlocksBroken())),
                    Placeholder.parsed("max_blocks", String.valueOf(config.getMaxBlocksPerDay()))
            );
        } else if (player.hasPermission("autotreechop.vip") && config.getLimitVipUsage()) {
            sendMessage(player, USAGE_MESSAGE,
                    Placeholder.parsed("current_uses", String.valueOf(playerConfig.getDailyUses())),
                    Placeholder.parsed("max_uses", String.valueOf(config.getVipUsesPerDay()))
            );
            sendMessage(player, BLOCKS_BROKEN_MESSAGE,
                    Placeholder.parsed("current_blocks", String.valueOf(playerConfig.getDailyBlocksBroken())),
                    Placeholder.parsed("max_blocks", String.valueOf(config.getVipBlocksPerDay()))
            );
        } else if (player.hasPermission("autotreechop.vip") && !config.getLimitVipUsage()) {
            sendMessage(player, USAGE_MESSAGE,
                    Placeholder.parsed("current_uses", String.valueOf(playerConfig.getDailyUses())),
                    Placeholder.parsed("max_uses", "∞")
            );
            sendMessage(player, BLOCKS_BROKEN_MESSAGE,
                    Placeholder.parsed("current_blocks", String.valueOf(playerConfig.getDailyBlocksBroken())),
                    Placeholder.parsed("max_blocks", "∞")
            );
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
            sendMessage(sender, ENABLED_FOR_OTHER_MESSAGE,
                    Placeholder.parsed("player", targetPlayer.getName()));
            sendMessage(targetPlayer, ENABLED_BY_OTHER_MESSAGE,
                    Placeholder.parsed("player", sender.getName()));
        } else {
            sendMessage(sender, DISABLED_FOR_OTHER_MESSAGE,
                    Placeholder.parsed("player", targetPlayer.getName()));
            sendMessage(targetPlayer, DISABLED_BY_OTHER_MESSAGE,
                    Placeholder.parsed("player", sender.getName()));
        }
    }

    private void toggleAutoTreeChop(Player player, UUID playerUUID) {
        PlayerConfig playerConfig = plugin.getPlayerConfig(playerUUID);
        boolean autoTreeChopEnabled = !playerConfig.isAutoTreeChopEnabled();
        playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);

        if (autoTreeChopEnabled) {
            sendMessage(player, ENABLED_MESSAGE);
        } else {
            sendMessage(player, DISABLED_MESSAGE);
        }
    }

    // Logic when using /atc enable-all disable-all
    private void toggleAutoTreeChopForAll(CommandSender sender, boolean autoTreeChopEnabled) {
        String messageKey = autoTreeChopEnabled ? ENABLED_BY_OTHER_MESSAGE : DISABLED_BY_OTHER_MESSAGE;

        // Use parallelStream for better performance
        Bukkit.getOnlinePlayers().parallelStream().forEach(onlinePlayer -> {
            UUID playerUUID = onlinePlayer.getUniqueId();
            PlayerConfig playerConfig = plugin.getPlayerConfig(playerUUID);
            playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);

            sendMessage(onlinePlayer, messageKey,
                    Placeholder.parsed("player", sender.getName()));
        });
    }
}