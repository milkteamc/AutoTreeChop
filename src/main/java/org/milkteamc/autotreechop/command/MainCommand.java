package org.milkteamc.autotreechop.command;

import org.milkteamc.autotreechop.AutoTreeChop;
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

        if (actor.isPlayer()) {
            org.milkteamc.autotreechop.PlayerConfig playerConfig = plugin.getPlayerConfig(actor.uniqueId());
            boolean autoTreeChopEnabled = !playerConfig.isAutoTreeChopEnabled();
            playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);

            if (autoTreeChopEnabled) {
                AutoTreeChop.sendMessage(actor.asPlayer(), AutoTreeChop.ENABLED_MESSAGE);
            } else {
                AutoTreeChop.sendMessage(actor.asPlayer(), AutoTreeChop.DISABLED_MESSAGE);
            }
        } else {
            AutoTreeChop.sendMessage(actor.sender(), AutoTreeChop.ONLY_PLAYERS_MESSAGE);
        }
    }
}
