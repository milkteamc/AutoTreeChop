/*
 * Copyright (C) 2026 MilkTeaMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.milkteamc.autotreechop.command;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.MessageKeys;
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
                MessageKeys.ABOUT_HEADER,
                Placeholder.parsed("version", plugin.getDescription().getVersion()));

        AutoTreeChop.sendMessage(sender, MessageKeys.ABOUT_LICENSE);
        AutoTreeChop.sendMessage(sender, MessageKeys.ABOUT_GITHUB);
        AutoTreeChop.sendMessage(sender, MessageKeys.ABOUT_MODRINTH);
    }
}
