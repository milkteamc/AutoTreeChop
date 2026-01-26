package org.milkteamc.autotreechop.command;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;

@Command({"atc", "autotreechop"})
public class UsageCommand {

    private final AutoTreeChop plugin;
    private final Config config;

    public UsageCommand(AutoTreeChop plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Subcommand("usage")
    public void usage(BukkitCommandActor actor) {
        if (!actor.isPlayer()) {
            AutoTreeChop.sendMessage(actor.sender(), AutoTreeChop.ONLY_PLAYERS_MESSAGE);
            return;
        }

        Player player = actor.asPlayer();
        org.milkteamc.autotreechop.PlayerConfig pConfig = plugin.getPlayerConfig(player.getUniqueId());

        boolean isVip = player.hasPermission("autotreechop.vip");
        boolean limitVip = config.getLimitVipUsage();

        String maxUsesStr;
        String maxBlocksStr;

        if (!isVip) {
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
                AutoTreeChop.USAGE_MESSAGE,
                Placeholder.parsed("current_uses", String.valueOf(pConfig.getDailyUses())),
                Placeholder.parsed("max_uses", maxUsesStr));

        AutoTreeChop.sendMessage(
                player,
                AutoTreeChop.BLOCKS_BROKEN_MESSAGE,
                Placeholder.parsed("current_blocks", String.valueOf(pConfig.getDailyBlocksBroken())),
                Placeholder.parsed("max_blocks", maxBlocksStr));
    }
}
