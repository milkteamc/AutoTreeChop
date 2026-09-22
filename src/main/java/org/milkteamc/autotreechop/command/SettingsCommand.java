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

import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.AutoTreeChopAPI;
import org.milkteamc.autotreechop.MessageKeys;
import org.milkteamc.autotreechop.PlayerPreferences;
import org.milkteamc.autotreechop.utils.PreferenceUtils;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.Suggest;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;

@Command({"atc", "autotreechop"})
public final class SettingsCommand {
    private final AutoTreeChop plugin;

    public SettingsCommand(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    @Subcommand("settings")
    @CommandPermission("autotreechop.settings")
    public void settings(
            BukkitCommandActor actor,
            @Optional @Suggest({"activation", "sneak-messages", "leaves", "replant", "reset"}) String setting,
            @Optional @Suggest({"default", "on", "off", "command", "sneak", "command-and-sneak", "hotkey", "disabled"})
                    String value) {
        if (!(actor.sender() instanceof Player player)) {
            AutoTreeChop.sendMessage(actor.sender(), MessageKeys.ONLY_PLAYERS);
            return;
        }
        if (!player.hasPermission("autotreechop.settings") || !player.hasPermission("autotreechop.use")) {
            AutoTreeChop.sendMessage(player, MessageKeys.NO_PERMISSION);
            return;
        }
        execute(player, player, setting, value, false);
    }

    @Subcommand("settings player")
    @CommandPermission("autotreechop.settings.other")
    public void settingsForPlayer(
            BukkitCommandActor actor,
            Player target,
            @Optional @Suggest({"activation", "sneak-messages", "leaves", "replant", "reset"}) String setting,
            @Optional @Suggest({"default", "on", "off", "command", "sneak", "command-and-sneak", "hotkey", "disabled"})
                    String value) {
        CommandSender sender = actor.sender();
        if (!sender.hasPermission("autotreechop.settings.other")) {
            AutoTreeChop.sendMessage(sender, MessageKeys.NO_PERMISSION);
            return;
        }
        execute(sender, target, setting, value, true);
    }

    private void execute(CommandSender sender, Player target, String setting, String value, boolean other) {
        var api = plugin.getAutoTreeChopAPI();
        var manager = plugin.getDataManager();
        if (api == null || manager == null) {
            AutoTreeChop.sendMessage(sender, MessageKeys.PLAYER_DATA_UNAVAILABLE);
            return;
        }
        synchronized (manager) {
            var saved = api.getPlayerPreferences(target.getUniqueId());
            if (saved.isEmpty()) {
                AutoTreeChop.sendMessage(sender, MessageKeys.PLAYER_DATA_UNAVAILABLE);
                return;
            }
            PlayerPreferences preferences = saved.get();
            if (setting == null) {
                show(sender, target, preferences, other);
                return;
            }
            setting = setting.toLowerCase(Locale.ROOT);
            try {
                PlayerPreferences updated;
                if (setting.equals("reset") && value == null) updated = PlayerPreferences.DEFAULTS;
                else if (value == null) throw new IllegalArgumentException();
                else
                    updated = switch (setting) {
                        case "activation" -> preferences.withActivation(PlayerPreferences.Activation.parse(value));
                        case "sneak-messages" -> preferences.withSneakMessages(PlayerPreferences.Toggle.parse(value));
                        case "leaves" -> preferences.withLeafRemoval(PlayerPreferences.Toggle.parse(value));
                        case "replant" -> preferences.withAutoReplant(PlayerPreferences.Toggle.parse(value));
                        default -> throw new IllegalArgumentException();
                    };
                AutoTreeChopAPI.ChangeResult result = api.setPlayerPreferences(target.getUniqueId(), updated);
                if (result == AutoTreeChopAPI.ChangeResult.UNAVAILABLE) {
                    AutoTreeChop.sendMessage(sender, MessageKeys.PLAYER_DATA_UNAVAILABLE);
                    return;
                }
                if (other) {
                    AutoTreeChop.sendMessage(
                            sender,
                            result == AutoTreeChopAPI.ChangeResult.UPDATED
                                    ? MessageKeys.SETTINGS_UPDATED_FOR_OTHER
                                    : MessageKeys.SETTINGS_UNCHANGED_FOR_OTHER,
                            Placeholder.unparsed("player", target.getName()));
                    if (result == AutoTreeChopAPI.ChangeResult.UPDATED && !sender.equals(target)) {
                        AutoTreeChop.sendMessage(
                                target,
                                MessageKeys.SETTINGS_UPDATED_BY_OTHER,
                                Placeholder.unparsed("player", sender.getName()));
                    }
                } else {
                    AutoTreeChop.sendMessage(
                            sender,
                            result == AutoTreeChopAPI.ChangeResult.UPDATED
                                    ? MessageKeys.SETTINGS_UPDATED
                                    : MessageKeys.SETTINGS_UNCHANGED);
                }
                show(sender, target, updated, other);
            } catch (IllegalArgumentException e) {
                AutoTreeChop.sendMessage(sender, other ? MessageKeys.SETTINGS_OTHER_USAGE : MessageKeys.SETTINGS_USAGE);
            }
        }
    }

    private void show(CommandSender sender, Player target, PlayerPreferences preferences, boolean other) {
        var config = plugin.getPluginConfig();
        if (other) {
            AutoTreeChop.sendMessage(
                    sender, MessageKeys.SETTINGS_FOR_OTHER, Placeholder.unparsed("player", target.getName()));
        }
        row(
                sender,
                "activation",
                preferences.activation().value(),
                PreferenceUtils.activation(preferences, config)
                        .name()
                        .toLowerCase(Locale.ROOT)
                        .replace('_', '-'));
        row(
                sender,
                "sneak-messages",
                preferences.sneakMessages().value(),
                PreferenceUtils.sneakMessages(preferences, config) ? "on" : "off");
        row(
                sender,
                "leaves",
                preferences.leafRemoval().value(),
                PreferenceUtils.leafRemoval(target, preferences, config) ? "on" : "off");
        row(
                sender,
                "replant",
                preferences.autoReplant().value(),
                PreferenceUtils.autoReplant(target, preferences, config) ? "on" : "off");
    }

    private static void row(CommandSender sender, String setting, String saved, String effective) {
        AutoTreeChop.sendMessage(
                sender,
                MessageKeys.SETTINGS_ENTRY,
                Placeholder.unparsed("setting", setting),
                Placeholder.unparsed("saved", saved),
                Placeholder.unparsed("effective", effective));
    }
}
