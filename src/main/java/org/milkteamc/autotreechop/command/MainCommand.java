package org.milkteamc.autotreechop.command;

import org.milkteamc.autotreechop.AutoTreeChop;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.annotation.Command;

import static org.milkteamc.autotreechop.AutoTreeChop.*;

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
                sendMessage(actor.asPlayer(), ENABLED_MESSAGE);
            } else {
                sendMessage(actor.asPlayer(), DISABLED_MESSAGE);
            }
        } else {
            sendMessage(actor.sender(), ONLY_PLAYERS_MESSAGE);
        }
    }
}
