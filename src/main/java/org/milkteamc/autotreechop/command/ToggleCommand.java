package org.milkteamc.autotreechop.command;

import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.PlayerConfig;
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
    public void toggle(BukkitCommandActor actor, @Optional Player targetPlayer) {
        if (targetPlayer == null) {
            if (!(actor.sender() instanceof Player player)) {
                AutoTreeChop.sendMessage(actor.sender(), AutoTreeChop.ONLY_PLAYERS_MESSAGE);
                return;
            }
            UUID playerUUID = player.getUniqueId();
            PlayerConfig playerConfig = plugin.getPlayerConfig(playerUUID);
            boolean autoTreeChopEnabled = !playerConfig.isAutoTreeChopEnabled();
            playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);

            if (autoTreeChopEnabled) {
                AutoTreeChop.sendMessage(player, AutoTreeChop.ENABLED_MESSAGE);
            } else {
                plugin.getConfirmationManager().clearPlayer(playerUUID); // UUID, not Player
                AutoTreeChop.sendMessage(player, AutoTreeChop.DISABLED_MESSAGE);
            }
            return;
        }

        if (!actor.sender().hasPermission("autotreechop.other")) {
            AutoTreeChop.sendMessage(actor.sender(), AutoTreeChop.NO_PERMISSION_MESSAGE);
            return;
        }

        UUID targetUUID = targetPlayer.getUniqueId();
        PlayerConfig playerConfig = plugin.getPlayerConfig(targetUUID);
        boolean autoTreeChopEnabled = !playerConfig.isAutoTreeChopEnabled();
        playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);

        if (autoTreeChopEnabled) {
            AutoTreeChop.sendMessage(
                    targetPlayer,
                    AutoTreeChop.ENABLED_BY_OTHER_MESSAGE,
                    Placeholder.parsed("player", actor.sender().getName()));
            AutoTreeChop.sendMessage(
                    actor.sender(),
                    AutoTreeChop.ENABLED_FOR_OTHER_MESSAGE,
                    Placeholder.parsed("player", targetPlayer.getName()));
        } else {
            plugin.getConfirmationManager().clearPlayer(targetUUID); // UUID, not Player
            AutoTreeChop.sendMessage(
                    targetPlayer,
                    AutoTreeChop.DISABLED_BY_OTHER_MESSAGE,
                    Placeholder.parsed("player", actor.sender().getName()));
            AutoTreeChop.sendMessage(
                    actor.sender(),
                    AutoTreeChop.DISABLED_FOR_OTHER_MESSAGE,
                    Placeholder.parsed("player", targetPlayer.getName()));
        }
    }

    @Subcommand("enable")
    @CommandPermission("autotreechop.use")
    public void enable(BukkitCommandActor actor, @Optional EntitySelector<Player> targetPlayers) {
        if (targetPlayers == null) {
            if (!(actor.sender() instanceof Player player)) {
                AutoTreeChop.sendMessage(actor.sender(), AutoTreeChop.ONLY_PLAYERS_MESSAGE);
                return;
            }
            plugin.getPlayerConfig(player.getUniqueId()).setAutoTreeChopEnabled(true);
            AutoTreeChop.sendMessage(player, AutoTreeChop.ENABLED_MESSAGE);
            return;
        }

        if (!actor.sender().hasPermission("autotreechop.other")) {
            AutoTreeChop.sendMessage(actor.sender(), AutoTreeChop.NO_PERMISSION_MESSAGE);
            return;
        }

        int count = 0;
        for (Player targetPlayer : targetPlayers) {
            plugin.getPlayerConfig(targetPlayer.getUniqueId()).setAutoTreeChopEnabled(true);
            count++;
            AutoTreeChop.sendMessage(
                    targetPlayer,
                    AutoTreeChop.ENABLED_BY_OTHER_MESSAGE,
                    Placeholder.parsed("player", actor.sender().getName()));
        }

        if (count == 1) {
            AutoTreeChop.sendMessage(
                    actor.sender(),
                    AutoTreeChop.ENABLED_FOR_OTHER_MESSAGE,
                    Placeholder.parsed("player", targetPlayers.iterator().next().getName()));
        } else if (count > 1) {
            AutoTreeChop.sendMessage(
                    actor.sender(), AutoTreeChop.ENABLED_FOR_OTHER_MESSAGE, Placeholder.parsed("player", "everyone"));
        }
    }

    @Subcommand("disable")
    @CommandPermission("autotreechop.use")
    public void disable(BukkitCommandActor actor, @Optional EntitySelector<Player> targetPlayers) {
        if (targetPlayers == null) {
            if (!(actor.sender() instanceof Player player)) {
                AutoTreeChop.sendMessage(actor.sender(), AutoTreeChop.ONLY_PLAYERS_MESSAGE);
                return;
            }
            UUID playerUUID = player.getUniqueId();
            plugin.getPlayerConfig(playerUUID).setAutoTreeChopEnabled(false);
            plugin.getConfirmationManager().clearPlayer(playerUUID); // <-- added
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
            plugin.getPlayerConfig(targetUUID).setAutoTreeChopEnabled(false);
            plugin.getConfirmationManager().clearPlayer(targetUUID); // <-- added
            count++;
            AutoTreeChop.sendMessage(
                    targetPlayer,
                    AutoTreeChop.DISABLED_BY_OTHER_MESSAGE,
                    Placeholder.parsed("player", actor.sender().getName()));
        }

        if (count == 1) {
            AutoTreeChop.sendMessage(
                    actor.sender(),
                    AutoTreeChop.DISABLED_FOR_OTHER_MESSAGE,
                    Placeholder.parsed("player", targetPlayers.iterator().next().getName()));
        } else if (count > 1) {
            AutoTreeChop.sendMessage(
                    actor.sender(), AutoTreeChop.DISABLED_FOR_OTHER_MESSAGE, Placeholder.parsed("player", "everyone"));
        }
    }
}
