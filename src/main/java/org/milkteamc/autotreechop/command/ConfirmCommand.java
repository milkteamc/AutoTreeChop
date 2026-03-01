package org.milkteamc.autotreechop.command;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.utils.ConfirmationManager.ConfirmReason;
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

        // consumePendingConfirmation atomically reads and removes the pending reason in
        // one step, avoiding the TOCTOU race that would exist if isConfirmationPending()
        // and getPendingReason() were called separately (the window could expire between
        // the two calls, causing getPendingReason to return null).
        ConfirmReason reason = plugin.getConfirmationManager().consumePendingConfirmation(uuid);

        if (reason == null) {
            AutoTreeChop.sendMessage(player, AutoTreeChop.NO_PENDING_CONFIRMATION_MESSAGE);
            return;
        }

        plugin.getConfirmationManager().recordSuccessfulChop(uuid, reason);
        AutoTreeChop.sendMessage(player, AutoTreeChop.CONFIRMATION_SUCCESS_MESSAGE);
    }
}
