package org.milkteamc.autotreechop.command;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.AutoTreeChop;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;

import static org.milkteamc.autotreechop.AutoTreeChop.*;

@Command({"atc", "autotreechop"})
public class ToggleCommand {
    
    private final AutoTreeChop plugin;
    
    public ToggleCommand(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    @Subcommand("toggle")
    @CommandPermission("autotreechop.other")
    public void toggle(BukkitCommandActor actor, Player targetPlayer) {

        java.util.UUID targetUUID = targetPlayer.getUniqueId();
        org.milkteamc.autotreechop.PlayerConfig playerConfig = plugin.getPlayerConfig(targetUUID);
        boolean autoTreeChopEnabled = !playerConfig.isAutoTreeChopEnabled();
        playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);

        if (autoTreeChopEnabled) {
            sendMessage(actor.sender(), ENABLED_FOR_OTHER_MESSAGE,
                    Placeholder.parsed("player", targetPlayer.getName()));
            sendMessage(targetPlayer, ENABLED_BY_OTHER_MESSAGE,
                    Placeholder.parsed("player", actor.sender().getName()));
        } else {
            sendMessage(actor.sender(), DISABLED_FOR_OTHER_MESSAGE,
                    Placeholder.parsed("player", targetPlayer.getName()));
            sendMessage(targetPlayer, DISABLED_BY_OTHER_MESSAGE,
                    Placeholder.parsed("player", actor.sender().getName()));
        }
    }
    
    @Subcommand("enable")
    public void enable(BukkitCommandActor actor, Player targetPlayer) {
        
    }
}
