package org.milkteamc.autotreechop.command;

import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.PlayerConfig;
import org.milkteamc.autotreechop.utils.BlockDiscoveryUtils;
import org.milkteamc.autotreechop.utils.ConfirmationManager.ChopData;
import org.milkteamc.autotreechop.utils.EffectUtils;
import org.milkteamc.autotreechop.utils.ProtectionCheckUtils.ProtectionHooks;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;

@Command({"atc", "autotreechop"})
public class ConfirmCommand {

    private final AutoTreeChop plugin;

    public ConfirmCommand(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    @Subcommand("confirm")
    @CommandPermission("autotreechop.use")
    public void confirm(BukkitCommandActor actor) {
        if (!(actor.sender() instanceof Player player)) {
            AutoTreeChop.sendMessage(actor.sender(), AutoTreeChop.ONLY_PLAYERS_MESSAGE);
            return;
        }

        UUID uuid = player.getUniqueId();

        // consumePendingConfirmation atomically reads and removes the pending entry in
        // one step, avoiding the TOCTOU race that would exist with separate
        // isConfirmationPending() / getPendingReason() calls.
        ChopData chop = plugin.getConfirmationManager().consumePendingConfirmation(uuid);

        if (chop == null) {
            AutoTreeChop.sendMessage(player, AutoTreeChop.NO_PENDING_CONFIRMATION_MESSAGE);
            return;
        }

        Config config = plugin.getPluginConfig();
        PlayerConfig playerConfig = plugin.getPlayerConfig(uuid);

        // The block may have been broken or replaced during the confirmation window
        // (e.g. another player cleared it). Re-validate before chopping.
        Block block = chop.blockLocation().getBlock();
        if (!BlockDiscoveryUtils.isLog(block.getType(), config)) {
            // Log is gone — treat as if there was no pending confirmation so the
            // player gets clear feedback rather than a silent no-op.
            AutoTreeChop.sendMessage(player, AutoTreeChop.NO_PENDING_CONFIRMATION_MESSAGE);
            return;
        }

        plugin.getConfirmationManager().recordSuccessfulChop(uuid, chop.reason(), false);
        AutoTreeChop.sendMessage(player, AutoTreeChop.CONFIRMATION_SUCCESS_MESSAGE);

        if (config.isVisualEffect()) {
            EffectUtils.showChopEffect(player, block);
        }

        ProtectionHooks hooks = new ProtectionHooks(
                plugin.isWorldGuardEnabled(),
                plugin.getWorldGuardHook(),
                plugin.isResidenceEnabled(),
                plugin.getResidenceHook(),
                plugin.isGriefPreventionEnabled(),
                plugin.getGriefPreventionHook(),
                plugin.isLandsEnabled(),
                plugin.getLandsHook());

        plugin.getTreeChopUtils()
                .chopTree(
                        block,
                        player,
                        config.isStopChoppingIfNotConnected(),
                        chop.tool(),
                        chop.blockLocation(),
                        config,
                        playerConfig,
                        hooks);
    }
}
