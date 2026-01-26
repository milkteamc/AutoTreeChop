package org.milkteamc.autotreechop.command;

import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.AutoTreeChop;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.bukkit.parameters.EntitySelector;

@Command({"atc", "autotreechop"})
public class ToggleCommand {

    private final AutoTreeChop plugin;

    public ToggleCommand(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    @Subcommand("toggle")
    @CommandPermission("autotreechop.use")
    public void toggle(BukkitCommandActor actor, @Optional EntitySelector<Player> targetPlayers) {
        if (targetPlayers == null) {
            if (!(actor.sender() instanceof Player)) {
                AutoTreeChop.sendMessage(actor.sender(), AutoTreeChop.ONLY_PLAYERS_MESSAGE);
                return;
            }
            Player player = (Player) actor.sender();
            UUID playerUUID = player.getUniqueId();
            org.milkteamc.autotreechop.PlayerConfig playerConfig = plugin.getPlayerConfig(playerUUID);
            boolean autoTreeChopEnabled = !playerConfig.isAutoTreeChopEnabled();
            playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);

            if (autoTreeChopEnabled) {
                AutoTreeChop.sendMessage(player, AutoTreeChop.ENABLED_MESSAGE);
            } else {
                AutoTreeChop.sendMessage(player, AutoTreeChop.DISABLED_MESSAGE);
            }
            return;
        }

        if (!actor.sender().hasPermission("autotreechop.other")) {
            AutoTreeChop.sendMessage(actor.sender(), AutoTreeChop.NO_PERMISSION_MESSAGE);
            return;
        }

        int count = 0;
        boolean lastState = false;

        for (Player targetPlayer : targetPlayers) {
            UUID targetUUID = targetPlayer.getUniqueId();
            org.milkteamc.autotreechop.PlayerConfig playerConfig = plugin.getPlayerConfig(targetUUID);
            boolean autoTreeChopEnabled = !playerConfig.isAutoTreeChopEnabled();
            playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);
            lastState = autoTreeChopEnabled;
            count++;

            if (autoTreeChopEnabled) {
                AutoTreeChop.sendMessage(
                        targetPlayer,
                        AutoTreeChop.ENABLED_BY_OTHER_MESSAGE,
                        Placeholder.parsed("player", actor.sender().getName()));
            } else {
                AutoTreeChop.sendMessage(
                        targetPlayer,
                        AutoTreeChop.DISABLED_BY_OTHER_MESSAGE,
                        Placeholder.parsed("player", actor.sender().getName()));
            }
        }

        if (count == 1) {
            Player firstPlayer = targetPlayers.iterator().next();
            if (lastState) {
                AutoTreeChop.sendMessage(
                        actor.sender(),
                        AutoTreeChop.ENABLED_FOR_OTHER_MESSAGE,
                        Placeholder.parsed("player", firstPlayer.getName()));
            } else {
                AutoTreeChop.sendMessage(
                        actor.sender(),
                        AutoTreeChop.DISABLED_FOR_OTHER_MESSAGE,
                        Placeholder.parsed("player", firstPlayer.getName()));
            }
        } else if (count > 1) {
            if (lastState) {
                AutoTreeChop.sendMessage(
                        actor.sender(),
                        AutoTreeChop.ENABLED_FOR_OTHER_MESSAGE,
                        Placeholder.parsed("player", "everyone"));
            } else {
                AutoTreeChop.sendMessage(
                        actor.sender(),
                        AutoTreeChop.DISABLED_FOR_OTHER_MESSAGE,
                        Placeholder.parsed("player", "everyone"));
            }
        }
    }

    @Subcommand("enable")
    @CommandPermission("autotreechop.use")
    public void enable(BukkitCommandActor actor, @Optional EntitySelector<Player> targetPlayers) {
        if (targetPlayers == null) {
            if (!(actor.sender() instanceof Player)) {
                AutoTreeChop.sendMessage(actor.sender(), AutoTreeChop.ONLY_PLAYERS_MESSAGE);
                return;
            }
            Player player = (Player) actor.sender();
            UUID playerUUID = player.getUniqueId();
            org.milkteamc.autotreechop.PlayerConfig playerConfig = plugin.getPlayerConfig(playerUUID);
            playerConfig.setAutoTreeChopEnabled(true);
            AutoTreeChop.sendMessage(player, AutoTreeChop.ENABLED_MESSAGE);
            return;
        }

        if (!actor.sender().hasPermission("autotreechop.other")) {
            AutoTreeChop.sendMessage(actor.sender(), AutoTreeChop.NO_PERMISSION_MESSAGE);
            return;
        }

        int count = 0;

        for (Player targetPlayer : targetPlayers) {
            UUID targetUUID = targetPlayer.getUniqueId();
            org.milkteamc.autotreechop.PlayerConfig playerConfig = plugin.getPlayerConfig(targetUUID);
            playerConfig.setAutoTreeChopEnabled(true);
            count++;

            AutoTreeChop.sendMessage(
                    targetPlayer,
                    AutoTreeChop.ENABLED_BY_OTHER_MESSAGE,
                    Placeholder.parsed("player", actor.sender().getName()));
        }

        if (count == 1) {
            Player firstPlayer = targetPlayers.iterator().next();
            AutoTreeChop.sendMessage(
                    actor.sender(),
                    AutoTreeChop.ENABLED_FOR_OTHER_MESSAGE,
                    Placeholder.parsed("player", firstPlayer.getName()));
        } else if (count > 1) {
            AutoTreeChop.sendMessage(
                    actor.sender(), AutoTreeChop.ENABLED_FOR_OTHER_MESSAGE, Placeholder.parsed("player", "everyone"));
        }
    }

    @Subcommand("disable")
    @CommandPermission("autotreechop.use")
    public void disable(BukkitCommandActor actor, @Optional EntitySelector<Player> targetPlayers) {
        if (targetPlayers == null) {
            if (!(actor.sender() instanceof Player)) {
                AutoTreeChop.sendMessage(actor.sender(), AutoTreeChop.ONLY_PLAYERS_MESSAGE);
                return;
            }
            Player player = (Player) actor.sender();
            UUID playerUUID = player.getUniqueId();
            org.milkteamc.autotreechop.PlayerConfig playerConfig = plugin.getPlayerConfig(playerUUID);
            playerConfig.setAutoTreeChopEnabled(false);
            AutoTreeChop.sendMessage(player, AutoTreeChop.DISABLED_MESSAGE);
            return;
        }

        if (!actor.sender().hasPermission("autotreechop.other")) {
            AutoTreeChop.sendMessage(actor.sender(), AutoTreeChop.NO_PERMISSION_MESSAGE);
            return;
        }

        int count = 0;

        for (Player targetPlayer : targetPlayers) {
            UUID targetUUID = targetPlayer.getUniqueId();
            org.milkteamc.autotreechop.PlayerConfig playerConfig = plugin.getPlayerConfig(targetUUID);
            playerConfig.setAutoTreeChopEnabled(false);
            count++;

            AutoTreeChop.sendMessage(
                    targetPlayer,
                    AutoTreeChop.DISABLED_BY_OTHER_MESSAGE,
                    Placeholder.parsed("player", actor.sender().getName()));
        }

        if (count == 1) {
            Player firstPlayer = targetPlayers.iterator().next();
            AutoTreeChop.sendMessage(
                    actor.sender(),
                    AutoTreeChop.DISABLED_FOR_OTHER_MESSAGE,
                    Placeholder.parsed("player", firstPlayer.getName()));
        } else if (count > 1) {
            AutoTreeChop.sendMessage(
                    actor.sender(), AutoTreeChop.DISABLED_FOR_OTHER_MESSAGE, Placeholder.parsed("player", "everyone"));
        }
    }
}
