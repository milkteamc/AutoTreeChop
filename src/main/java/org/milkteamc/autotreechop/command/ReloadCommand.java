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
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.MessageKeys;
import org.milkteamc.autotreechop.configuration.ConfigLoadException;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;

@Command({"atc", "autotreechop"})
public class ReloadCommand {

    private final AutoTreeChop plugin;
    private final Config config;

    public ReloadCommand(AutoTreeChop plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Subcommand("reload")
    @CommandPermission("autotreechop.reload")
    public void reload(BukkitCommandActor actor) {

        try {
            config.load();
        } catch (ConfigLoadException e) {
            plugin.getLogger().warning("Configuration reload rejected: " + e.getMessage());
            AutoTreeChop.sendMessage(
                    actor.sender(), MessageKeys.CONFIG_RELOAD_FAILED, Placeholder.unparsed("reason", e.getMessage()));
            return;
        }

        plugin.getTranslationManager()
                .reload(
                        config.getLocale() == null ? java.util.Locale.getDefault() : config.getLocale(),
                        config.isUseClientLocale());

        AutoTreeChop.sendMessage(actor.sender(), MessageKeys.CONFIG_RELOADED);
        if (!config.getRestartRequiredSettings().isEmpty()) {
            AutoTreeChop.sendMessage(
                    actor.sender(),
                    MessageKeys.CONFIG_RESTART_REQUIRED,
                    Placeholder.unparsed("settings", String.join(", ", config.getRestartRequiredSettings())));
        }
    }
}
