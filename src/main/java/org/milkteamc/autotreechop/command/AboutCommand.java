package org.milkteamc.autotreechop.command;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.milkteamc.autotreechop.AutoTreeChop;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;

@Command({"atc", "autotreechop"})
public class AboutCommand {

    private final AutoTreeChop plugin;

    public AboutCommand(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    @Subcommand("about")
    public void about(BukkitCommandActor actor) {
        CommandSender sender = actor.sender();

        AutoTreeChop.sendMessage(
                sender,
                AutoTreeChop.ABOUT_HEADER,
                Placeholder.parsed("version", plugin.getDescription().getVersion()));

        AutoTreeChop.sendMessage(sender, AutoTreeChop.ABOUT_LICENSE);
        AutoTreeChop.sendMessage(sender, AutoTreeChop.ABOUT_GITHUB);
        AutoTreeChop.sendMessage(sender, AutoTreeChop.ABOUT_DISCORD);
        AutoTreeChop.sendMessage(sender, AutoTreeChop.ABOUT_MODRINTH);
    }
}
