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
 
package org.milkteamc.autotreechop.events;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import org.milkteamc.autotreechop.MessageKeys;
import org.milkteamc.autotreechop.PlayerConfig;
import org.milkteamc.autotreechop.utils.AsyncTaskScheduler;
import org.milkteamc.autotreechop.utils.BlockDiscoveryUtils;
import org.milkteamc.autotreechop.utils.ConfirmationManager;
import org.milkteamc.autotreechop.utils.ConfirmationManager.ChopData;
import org.milkteamc.autotreechop.utils.ConfirmationManager.ConfirmReason;
import org.milkteamc.autotreechop.utils.EffectUtils;
import org.milkteamc.autotreechop.utils.PermissionUtils;
import org.milkteamc.autotreechop.utils.ProtectionCheckUtils.ProtectionHooks;
import org.milkteamc.autotreechop.utils.SessionManager;

public class BlockBreakListener implements Listener {

    private final AutoTreeChop plugin;
    private final AsyncTaskScheduler scheduler;

    /**
     * Players who currently have an async leaf-check in flight.
     * Guards against the race where the player breaks a second log before the
     * first async check completes, which would start two concurrent chop pipelines
     * for the same player before either has registered with SessionManager.
     *
     * <p>A player is added just before the async task is submitted and removed
     * (via try-finally) when the sync callback finishes, whether it dispatches a
     * chop, sets a pending confirmation, or discards the event (player offline).
     */
    private final Set<UUID> leafCheckInProgress = ConcurrentHashMap.newKeySet();

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

        if (plugin.getCooldownManager().isInCooldown(playerUUID)) {
            long remaining = plugin.getCooldownManager().getRemainingCooldown(playerUUID);
            AutoTreeChop.sendMessage(
                    player,
                    MessageKeys.STILL_IN_COOLDOWN,
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
            AutoTreeChop.sendMessage(player, MessageKeys.HIT_MAX_USAGE);
            return;
        }

        // Limits cleared — check for a pending confirmation first.
        ConfirmationManager confirmationManager = plugin.getConfirmationManager();
        ChopData pending = confirmationManager.consumePendingConfirmation(playerUUID);

        event.setCancelled(true);

        if (pending != null) {
            // Player confirmed by breaking a log within the confirmation window.
            // Skip the leaf check entirely; grace is determined by the original reason.
            confirmationManager.recordSuccessfulChop(playerUUID, pending.reason(), false);
            AutoTreeChop.sendMessage(player, MessageKeys.CONFIRMATION_SUCCESS);
            dispatchChop(player, playerConfig, block, tool, location, config);
            return;
        }

        // Guard against concurrent leaf checks for the same player.
        // If a check is already in flight we simply eat this break — the log is
        // still present (event was cancelled) so the player can try again.
        if (!leafCheckInProgress.add(playerUUID)) {
            return;
        }

        // Pre-capture chunk snapshots on the main/region thread (world access is required
        // here), then read them on an async thread (snapshots are immutable — thread-safe).
        int radius = config.getNoLeavesDetectionRadius();
        Map<Long, ChunkSnapshot> snapshots = captureLeafCheckSnapshots(block, radius);

        // Clone tool now so we have stable values for the async path.
        ItemStack frozenTool = tool.clone();
        Location frozenLocation = location;

        scheduler.runTaskAsync(() -> {
            boolean hasLeaves = hasNearbyLeaves(block, radius, config, snapshots);

            // Return to the main/region thread to act on the result.
            scheduler.runTaskAtLocation(frozenLocation, () -> {
                // try-finally guarantees leafCheckInProgress is cleared on every exit path.
                try {
                    if (!player.isOnline()) return;

                    ConfirmReason reason = confirmationManager.getConfirmationReason(playerUUID, hasLeaves);

                    if (reason != null) {
                        // Store the chop parameters so /atc confirm can fire the chop
                        // without requiring the player to physically re-break the log.
                        confirmationManager.setPendingConfirmation(playerUUID, reason, frozenLocation, frozenTool);

                        String timeoutStr = String.valueOf(config.getConfirmationWindowSeconds());
                        String messageKey =
                                switch (reason) {
                                    case IDLE_OR_REJOIN -> MessageKeys.CONFIRMATION_REQUIRED_IDLE;
                                    case NO_LEAVES -> MessageKeys.CONFIRMATION_REQUIRED_NO_LEAVES;
                                    case BOTH -> MessageKeys.CONFIRMATION_REQUIRED_BOTH;
                                };
                        AutoTreeChop.sendMessage(player, messageKey, Placeholder.parsed("timeout", timeoutStr));
                        return;
                    }

                    confirmationManager.recordSuccessfulChop(playerUUID, null, hasLeaves);
                    dispatchChop(player, playerConfig, block, frozenTool, frozenLocation, config);
                } finally {
                    leafCheckInProgress.remove(playerUUID);
                }
            });
        });
    }

    void dispatchChop(
            Player player, PlayerConfig playerConfig, Block block, ItemStack tool, Location location, Config config) {

        if (config.isVisualEffect()) {
            EffectUtils.showChopEffect(player, block);
        }

        ProtectionHooks hooks = buildProtectionHooks();

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
     * Builds a {@link ProtectionHooks} snapshot from the plugin's current hook state.
     *
     * <p>Extracted from {@link #dispatchChop} so that the hook wiring lives in one
     * place and future hook additions only need to be made here.
     */
    private ProtectionHooks buildProtectionHooks() {
        return new ProtectionHooks(
                plugin.isWorldGuardEnabled(),
                plugin.getWorldGuardHook(),
                plugin.isResidenceEnabled(),
                plugin.getResidenceHook(),
                plugin.isGriefPreventionEnabled(),
                plugin.getGriefPreventionHook(),
                plugin.isLandsEnabled(),
                plugin.getLandsHook());
    }

    /**
     * Captures {@link ChunkSnapshot}s for all chunks within the leaf-detection radius.
     *
     * <p>Must be called on the main/region thread since it accesses live world state.
     * Once captured, the returned snapshots are immutable and safe to read on any thread.
     */
    private Map<Long, ChunkSnapshot> captureLeafCheckSnapshots(Block log, int radius) {
        World world = log.getWorld();
        int cx = log.getX();
        int cz = log.getZ();
        Map<Long, ChunkSnapshot> snapshots = new HashMap<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = (cx + dx) >> 4;
                int chunkZ = (cz + dz) >> 4;
                if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
                long key = chunkKey(chunkX, chunkZ);
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
    private static boolean hasNearbyLeaves(Block log, int radius, Config config, Map<Long, ChunkSnapshot> snapshots) {
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

                    long key = chunkKey(x >> 4, z >> 4);
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

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
