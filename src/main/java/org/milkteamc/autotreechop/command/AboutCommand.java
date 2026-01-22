package org.milkteamc.autotreechop.command;

import org.milkteamc.autotreechop.AutoTreeChop;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;

@Command({"atc", "autotreechop"})
public class AboutCommand {
    
    private final AutoTreeChop plugin;
    
    public AboutCommand(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    @Subcommand("about")
    public void about(BukkitCommandActor actor) {
        
        actor.sender().sendMessage("AutoTreeChop - " + plugin.getDescription().getVersion() + " is made by MilkTeaMC team and contributors");
        actor.sender().sendMessage("This JAR and the source code is licensed under the GNU General Public License v3.0 (GPL-3.0)");
        actor.sender().sendMessage("GitHub: https://github.com/milkteamc/autotreechop");
        actor.sender().sendMessage("Discord: https://discord.gg/uQ4UXANnP2");
        actor.sender().sendMessage("Modrinth: https://modrinth.com/plugin/autotreechop");
    }
}
