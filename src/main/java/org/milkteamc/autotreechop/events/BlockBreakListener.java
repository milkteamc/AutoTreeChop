package org.milkteamc.autotreechop.events;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.ChunkSnapshot;
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

        // consumePendingConfirmation is a single atomic read-and-remove, eliminating
        // the TOCTOU race that existed when isConfirmationPending() and getPendingReason()
        // were called separately.
        ConfirmReason pendingReason = confirmationManager.consumePendingConfirmation(playerUUID);

        if (pendingReason != null) {
            // Player is inside the confirmation window and just broke a log — confirmed!
            confirmationManager.recordSuccessfulChop(playerUUID, pendingReason);
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
     * <p>Uses {@link ChunkSnapshot} so that each chunk's block data is read once
     * into a fast array-backed snapshot rather than issuing a separate
     * {@code world.getBlockAt()} call per coordinate. At radius 6 up to ~2 197
     * blocks are checked; without snapshots every call is a full world-access on
     * the main thread. With snapshots only a handful of chunks need to be
     * snapshotted, and subsequent reads within the same chunk are O(1) array
     * lookups. Unloaded chunks are skipped — a player breaking a block can only
     * ever be in a loaded chunk, and adjacent chunks within view-distance are
     * always loaded too.
     *
     * <p>Short-circuits on the first leaf found for performance.
     */
    private static boolean hasNearbyLeaves(Block log, Config config) {
        int radius = config.getNoLeavesDetectionRadius();
        World world = log.getWorld();
        int cx = log.getX();
        int cy = log.getY();
        int cz = log.getZ();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        // Cache one ChunkSnapshot per chunk — each snapshot is created once and
        // subsequent block reads from it are pure array lookups.
        Map<Long, ChunkSnapshot> chunkCache = new HashMap<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;

                    if (y < minY || y >= maxY) continue;

                    int chunkX = x >> 4;
                    int chunkZ = z >> 4;

                    // Skip unloaded chunks rather than forcing a load.
                    if (!world.isChunkLoaded(chunkX, chunkZ)) continue;

                    long chunkKey = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
                    ChunkSnapshot snapshot = chunkCache.computeIfAbsent(
                            chunkKey, k -> world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(false, false, false));

                    Material type = snapshot.getBlockType(x & 15, y, z & 15);
                    if (BlockDiscoveryUtils.isLeafBlock(type, config)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
