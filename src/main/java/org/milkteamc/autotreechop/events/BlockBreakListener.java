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
import org.milkteamc.autotreechop.utils.AsyncTaskScheduler;
import org.milkteamc.autotreechop.utils.BlockDiscoveryUtils;
import org.milkteamc.autotreechop.utils.ConfirmationManager;
import org.milkteamc.autotreechop.utils.ConfirmationManager.ConfirmReason;
import org.milkteamc.autotreechop.utils.EffectUtils;
import org.milkteamc.autotreechop.utils.PermissionUtils;
import org.milkteamc.autotreechop.utils.ProtectionCheckUtils.ProtectionHooks;
import org.milkteamc.autotreechop.utils.SessionManager;

public class BlockBreakListener implements Listener {

    private final AutoTreeChop plugin;
    private final AsyncTaskScheduler scheduler;

    public BlockBreakListener(AutoTreeChop plugin) {
        this.plugin = plugin;
        this.scheduler = new AsyncTaskScheduler(plugin);
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
        ConfirmReason pendingReason = confirmationManager.consumePendingConfirmation(playerUUID);

        // Cancel the event now — from this point we own the block break.
        // chopTree handles the actual breaking itself via breakNaturally().
        event.setCancelled(true);

        if (pendingReason != null) {
            // Player confirmed by breaking a log within the confirmation window.
            // Skip the leaf check entirely; grace is determined by the original reason.
            confirmationManager.recordSuccessfulChop(playerUUID, pendingReason, false);
            AutoTreeChop.sendMessage(player, AutoTreeChop.CONFIRMATION_SUCCESS_MESSAGE);
            dispatchChop(player, playerConfig, block, tool, location, config);
            return;
        }

        // Run cheap synchronous guards before doing any async work.
        if (plugin.getCooldownManager().isInCooldown(playerUUID)) {
            long remaining = plugin.getCooldownManager().getRemainingCooldown(playerUUID);
            AutoTreeChop.sendMessage(
                    player,
                    AutoTreeChop.STILL_IN_COOLDOWN_MESSAGE,
                    Placeholder.parsed("cooldown_time", String.valueOf(remaining)));
            return;
        }

        if (!PermissionUtils.hasVipBlock(player, playerConfig, config)
                && playerConfig.getDailyBlocksBroken() >= config.getMaxBlocksPerDay()) {
            EffectUtils.sendMaxBlockLimitReachedMessage(player, block);
            return;
        }

        if (!PermissionUtils.hasVipUses(player, playerConfig, config)
                && playerConfig.getDailyUses() >= config.getMaxUsesPerDay()) {
            AutoTreeChop.sendMessage(player, AutoTreeChop.HIT_MAX_USAGE_MESSAGE);
            return;
        }

        // Pre-capture chunk snapshots on the main/region thread (world access is required
        // here), then read them on an async thread (snapshots are immutable — thread-safe).
        Map<Long, ChunkSnapshot> snapshots = captureLeafCheckSnapshots(block, config);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean hasLeaves = hasNearbyLeaves(block, config, snapshots);

            // Return to the main/region thread to act on the result.
            scheduler.runTaskAtLocation(location, () -> {
                if (!player.isOnline()) return;

                ConfirmReason reason = confirmationManager.getConfirmationReason(playerUUID, hasLeaves);

                if (reason != null) {
                    confirmationManager.setPendingConfirmation(playerUUID, reason);
                    String timeoutStr = String.valueOf(config.getConfirmationWindowSeconds());
                    String messageKey =
                            switch (reason) {
                                case IDLE_OR_REJOIN -> AutoTreeChop.CONFIRMATION_REQUIRED_IDLE_MESSAGE;
                                case NO_LEAVES -> AutoTreeChop.CONFIRMATION_REQUIRED_NO_LEAVES_MESSAGE;
                                case BOTH -> AutoTreeChop.CONFIRMATION_REQUIRED_BOTH_MESSAGE;
                            };
                    AutoTreeChop.sendMessage(player, messageKey, Placeholder.parsed("timeout", timeoutStr));
                    return;
                }

                confirmationManager.recordSuccessfulChop(playerUUID, null, hasLeaves);
                dispatchChop(player, playerConfig, block, tool, location, config);
            });
        });
    }

    private void dispatchChop(
            Player player, PlayerConfig playerConfig, Block block, ItemStack tool, Location location, Config config) {

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
                        tool,
                        location,
                        config,
                        playerConfig,
                        hooks);
    }

    /**
     * Captures {@link ChunkSnapshot}s for all chunks within the leaf-detection radius.
     *
     * <p>Must be called on the main/region thread since it accesses live world state.
     * Once captured, the returned snapshots are immutable and safe to read on any thread.
     */
    private Map<Long, ChunkSnapshot> captureLeafCheckSnapshots(Block log, Config config) {
        int radius = config.getNoLeavesDetectionRadius();
        World world = log.getWorld();
        int cx = log.getX();
        int cz = log.getZ();
        Map<Long, ChunkSnapshot> snapshots = new HashMap<>();

        // Only the horizontal extent determines which chunks to capture;
        // a single snapshot covers the full vertical column of a chunk.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = (cx + dx) >> 4;
                int chunkZ = (cz + dz) >> 4;
                if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
                long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
                snapshots.computeIfAbsent(
                        key, k -> world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(false, false, false));
            }
        }
        return snapshots;
    }

    /**
     * Returns {@code true} if there is at least one leaf block within the configured
     * detection radius centred on the given log.
     *
     * <p>Safe to call from an async thread — all block data is read from the
     * pre-captured {@code snapshots}, which are immutable. Short-circuits on the
     * first leaf found.
     */
    private static boolean hasNearbyLeaves(Block log, Config config, Map<Long, ChunkSnapshot> snapshots) {
        int radius = config.getNoLeavesDetectionRadius();
        World world = log.getWorld();
        int cx = log.getX();
        int cy = log.getY();
        int cz = log.getZ();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    if (y < minY || y >= maxY) continue;

                    long key = ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
                    ChunkSnapshot snapshot = snapshots.get(key);
                    if (snapshot == null) continue;

                    if (BlockDiscoveryUtils.isLeafBlock(snapshot.getBlockType(x & 15, y, z & 15), config)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
