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
import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.MessageKeys;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;

@Command({"atc", "autotreechop"})
public class UsageCommand {

    private final AutoTreeChop plugin;
    private final Config config;

    public UsageCommand(AutoTreeChop plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Subcommand("usage")
    @CommandPermission("autotreechop.use")
    public void usage(BukkitCommandActor actor) {
        if (!actor.isPlayer()) {
            AutoTreeChop.sendMessage(actor.sender(), MessageKeys.ONLY_PLAYERS);
            return;
        }

        Player player = actor.asPlayer();
        org.milkteamc.autotreechop.PlayerConfig pConfig =
                plugin.getDataManager().getPlayerConfig(player.getUniqueId());

        boolean isVip = player.hasPermission("autotreechop.vip");
        boolean limitVip = config.getLimitVipUsage();

        String maxUsesStr;
        String maxBlocksStr;

        if (!config.getLimitUsage()) {
            maxUsesStr = "∞";
            maxBlocksStr = "∞";
        } else if (!isVip) {
            maxUsesStr = String.valueOf(config.getMaxUsesPerDay());
            maxBlocksStr = String.valueOf(config.getMaxBlocksPerDay());
        } else if (limitVip) {
            maxUsesStr = String.valueOf(config.getVipUsesPerDay());
            maxBlocksStr = String.valueOf(config.getVipBlocksPerDay());
        } else {
            maxUsesStr = "∞";
            maxBlocksStr = "∞";
        }

        AutoTreeChop.sendMessage(
                player,
                MessageKeys.USAGE,
                Placeholder.parsed("current_uses", String.valueOf(pConfig.getDailyUses())),
                Placeholder.parsed("max_uses", maxUsesStr));

        AutoTreeChop.sendMessage(
                player,
                MessageKeys.BLOCKS_BROKEN,
                Placeholder.parsed("current_blocks", String.valueOf(pConfig.getDailyBlocksBroken())),
                Placeholder.parsed("max_blocks", maxBlocksStr));
    }
}
