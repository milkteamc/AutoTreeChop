package org.milkteamc.autotreechop.events;

import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.PlayerConfig;
import org.milkteamc.autotreechop.utils.BlockDiscoveryUtils;
import org.milkteamc.autotreechop.utils.ConfirmationManager;
import org.milkteamc.autotreechop.utils.ConfirmationManager.ConfirmReason;
import org.milkteamc.autotreechop.utils.EffectUtils;
import org.milkteamc.autotreechop.utils.PermissionUtils;
import org.milkteamc.autotreechop.utils.ProtectionCheckUtils.ProtectionHooks;
import org.milkteamc.autotreechop.utils.SessionManager;

public class BlockBreakListener implements Listener {

    private final AutoTreeChop plugin;

    public BlockBreakListener(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        PlayerConfig playerConfig = plugin.getPlayerConfig(playerUUID);
        Block block = event.getBlock();
        ItemStack tool = player.getInventory().getItemInMainHand();
        Location location = block.getLocation();
        Config config = plugin.getPluginConfig();

        if (BlockDiscoveryUtils.isLeafBlock(block.getType(), config)) {
            if (SessionManager.getInstance().hasActiveLeafRemovalSession(playerUUID.toString())) {
                return;
            }
        }

        if (SessionManager.getInstance().isLocationProcessing(playerUUID, location)) {
            return;
        }

        Material material = block.getType();

        if (!playerConfig.isAutoTreeChopEnabled() || !BlockDiscoveryUtils.isLog(material, config)) {
            return;
        }

        ConfirmationManager confirmationManager = plugin.getConfirmationManager();
        String timeoutStr = String.valueOf(config.getConfirmationWindowSeconds());

        if (confirmationManager.isConfirmationPending(playerUUID)) {
            // Player is inside the confirmation window and just broke a log — confirmed!
            ConfirmReason confirmedReason = confirmationManager.getPendingReason(playerUUID);
            confirmationManager.recordSuccessfulChop(playerUUID, confirmedReason);
            AutoTreeChop.sendMessage(player, AutoTreeChop.CONFIRMATION_SUCCESS_MESSAGE);
            // Fall through to normal ATC processing below.

        } else {
            // Check whether confirmation is required for this chop.
            boolean hasLeaves = hasNearbyLeaves(block, config);
            ConfirmReason reason = confirmationManager.getConfirmationReason(playerUUID, hasLeaves);

            if (reason != null) {
                // Confirmation required — cancel the break, warn the player, open window.
                event.setCancelled(true);
                confirmationManager.setPendingConfirmation(playerUUID, reason);

                String messageKey =
                        switch (reason) {
                            case IDLE_OR_REJOIN -> AutoTreeChop.CONFIRMATION_REQUIRED_IDLE_MESSAGE;
                            case NO_LEAVES -> AutoTreeChop.CONFIRMATION_REQUIRED_NO_LEAVES_MESSAGE;
                            case BOTH -> AutoTreeChop.CONFIRMATION_REQUIRED_BOTH_MESSAGE;
                        };

                AutoTreeChop.sendMessage(player, messageKey, Placeholder.parsed("timeout", timeoutStr));
                return;

            } else {
                // Normal chop — no confirmation needed. Reset idle timer.
                confirmationManager.recordSuccessfulChop(playerUUID, null);
                // Fall through to normal ATC processing below.
            }
        }

        if (plugin.getCooldownManager().isInCooldown(playerUUID)) {
            long remainingCooldown = plugin.getCooldownManager().getRemainingCooldown(playerUUID);
            AutoTreeChop.sendMessage(
                    player,
                    AutoTreeChop.STILL_IN_COOLDOWN_MESSAGE,
                    Placeholder.parsed("cooldown_time", String.valueOf(remainingCooldown)));
            return;
        }

        if (!PermissionUtils.hasVipBlock(player, playerConfig, config)) {
            if (playerConfig.getDailyBlocksBroken() >= config.getMaxBlocksPerDay()) {
                EffectUtils.sendMaxBlockLimitReachedMessage(player, block);
                return;
            }
        }

        if (!PermissionUtils.hasVipUses(player, playerConfig, config)
                && playerConfig.getDailyUses() >= config.getMaxUsesPerDay()) {
            AutoTreeChop.sendMessage(player, AutoTreeChop.HIT_MAX_USAGE_MESSAGE);
            return;
        }

        if (config.isVisualEffect()) {
            EffectUtils.showChopEffect(player, block);
        }

        event.setCancelled(true);

        ProtectionHooks hooks = new ProtectionHooks(
                plugin.isWorldGuardEnabled(), plugin.getWorldGuardHook(),
                plugin.isResidenceEnabled(), plugin.getResidenceHook(),
                plugin.isGriefPreventionEnabled(), plugin.getGriefPreventionHook(),
                plugin.isLandsEnabled(), plugin.getLandsHook());

        plugin.getTreeChopUtils()
                .chopTree(
                        block,
                        player,
                        config.isStopChoppingIfNotConnected(),
                        tool,
                        location,
                        config,
                        playerConfig,
                        hooks);
    }

    /**
     * Returns {@code true} if there is at least one leaf block within the
     * configured detection radius centred on the given log.
     *
     * <p>Uses the same {@code BlockDiscoveryUtils.isLeafBlock} and leaf-type set
     * that the rest of the plugin uses, so "what counts as a leaf" stays
     * consistent across all features.
     *
     * <p>The radius comes from {@code Config#getNoLeavesDetectionRadius()} — a
     * dedicated setting separate from the leaf-<em>removal</em> radius so that
     * changing removal behaviour doesn't accidentally break detection.
     * Short-circuits on the first leaf found for performance.
     */
    private static boolean hasNearbyLeaves(Block log, Config config) {
        int radius = config.getNoLeavesDetectionRadius();
        World world = log.getWorld();
        int cx = log.getX();
        int cy = log.getY();
        int cz = log.getZ();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (BlockDiscoveryUtils.isLeafBlock(
                            world.getBlockAt(cx + dx, cy + dy, cz + dz).getType(), config)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
