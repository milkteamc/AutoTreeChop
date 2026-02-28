package org.milkteamc.autotreechop.command;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.PlayerConfig;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;

@Command({"atc", "autotreechop"})
public class MainCommand {

    private final AutoTreeChop plugin;

    public MainCommand(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    @Command({"atc", "autotreechop"})
    @CommandPermission("autotreechop.use")
    public void main(BukkitCommandActor actor) {
        if (!actor.isPlayer()) {
            AutoTreeChop.sendMessage(actor.sender(), AutoTreeChop.ONLY_PLAYERS_MESSAGE);
            return;
        }

        Player player = actor.asPlayer();
        UUID playerUUID = player.getUniqueId();
        PlayerConfig playerConfig = plugin.getPlayerConfig(playerUUID);
        boolean autoTreeChopEnabled = !playerConfig.isAutoTreeChopEnabled();
        playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);

        if (autoTreeChopEnabled) {
            AutoTreeChop.sendMessage(player, AutoTreeChop.ENABLED_MESSAGE);
        } else {
            plugin.getConfirmationManager().clearPlayer(playerUUID);
            AutoTreeChop.sendMessage(player, AutoTreeChop.DISABLED_MESSAGE);
        }
    }
}
