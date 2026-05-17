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

import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.MessageKeys;
import org.milkteamc.autotreechop.PlayerConfig;
import org.milkteamc.autotreechop.hooks.HookManager;
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
            AutoTreeChop.sendMessage(actor.sender(), MessageKeys.ONLY_PLAYERS);
            return;
        }

        UUID uuid = player.getUniqueId();

        // consumePendingConfirmation atomically reads and removes the pending entry in
        // one step, avoiding the TOCTOU race that would exist with separate
        // isConfirmationPending() / getPendingReason() calls.
        ChopData chop = plugin.getConfirmationManager().consumePendingConfirmation(uuid);

        if (chop == null) {
            AutoTreeChop.sendMessage(player, MessageKeys.NO_PENDING_CONFIRMATION);
            return;
        }

        Config config = plugin.getPluginConfig();
        PlayerConfig playerConfig = plugin.getDataManager().getPlayerConfig(uuid);

        // The block may have been broken or replaced during the confirmation window
        // (e.g. another player cleared it). Re-validate before chopping.
        Block block = chop.blockLocation().getBlock();
        if (!BlockDiscoveryUtils.isLog(block.getType(), config)) {
            // Log is gone — treat as if there was no pending confirmation so the
            // player gets clear feedback rather than a silent no-op.
            AutoTreeChop.sendMessage(player, MessageKeys.NO_PENDING_CONFIRMATION);
            return;
        }

        plugin.getConfirmationManager().recordSuccessfulChop(uuid, chop.reason(), false);
        AutoTreeChop.sendMessage(player, MessageKeys.CONFIRMATION_SUCCESS);

        if (config.isVisualEffect()) {
            EffectUtils.showChopEffect(player, block);
        }

        HookManager hookManager = plugin.getHookManager();
        ProtectionHooks hooks = new ProtectionHooks(
                hookManager.isWorldGuardEnabled(),
                hookManager.getWorldGuardHook(),
                hookManager.isResidenceEnabled(),
                hookManager.getResidenceHook(),
                hookManager.isGriefPreventionEnabled(),
                hookManager.getGriefPreventionHook(),
                hookManager.isLandsEnabled(),
                hookManager.getLandsHook());

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
