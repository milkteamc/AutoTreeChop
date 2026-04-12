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
import org.milkteamc.autotreechop.MessageKeys;
import org.milkteamc.autotreechop.PlayerConfig;
import org.milkteamc.autotreechop.hooks.HookManager;
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

    public BlockBreakListener(AutoTreeChop plugin) {
        this.plugin = plugin;
        this.scheduler = new AsyncTaskScheduler(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        PlayerConfig playerConfig = plugin.getDataManager().getPlayerConfig(playerUUID);
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
        ChopData pending = confirmationManager.consumePendingConfirmation(playerUUID);

        if (pending != null) {
            event.setCancelled(true);
            confirmationManager.recordSuccessfulChop(playerUUID, pending.reason(), false);
            AutoTreeChop.sendMessage(player, MessageKeys.CONFIRMATION_SUCCESS);
            dispatchChop(player, playerConfig, block, tool, location, config);
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

        event.setCancelled(true);

        if (!SessionManager.getInstance().startLeafCheck(playerUUID)) {
            return;
        }

        int radius = config.getNoLeavesDetectionRadius();
        Map<Long, ChunkSnapshot> snapshots = captureLeafCheckSnapshots(block, radius);

        ItemStack frozenTool = tool.clone();
        Location frozenLocation = location;

        scheduler.runTaskAsync(() -> {
            boolean hasLeaves = hasNearbyLeaves(block, radius, config, snapshots);

            scheduler.runTaskAtLocation(frozenLocation, () -> {
                try {
                    if (!player.isOnline()) return;

                    ConfirmReason reason = confirmationManager.getConfirmationReason(playerUUID, hasLeaves);

                    if (reason != null) {
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
                    SessionManager.getInstance().finishLeafCheck(playerUUID);
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

    private ProtectionHooks buildProtectionHooks() {
        HookManager hm = plugin.getHookManager();
        return new ProtectionHooks(
                hm.isWorldGuardEnabled(),
                hm.getWorldGuardHook(),
                hm.isResidenceEnabled(),
                hm.getResidenceHook(),
                hm.isGriefPreventionEnabled(),
                hm.getGriefPreventionHook(),
                hm.isLandsEnabled(),
                hm.getLandsHook());
    }

    private Map<Long, ChunkSnapshot> captureLeafCheckSnapshots(Block log, int radius) {
        World world = log.getWorld();
        int cx = log.getX();
        int cz = log.getZ();
        Map<Long, ChunkSnapshot> snapshots = new HashMap<>();

        int minChunkX = (cx - radius) >> 4;
        int maxChunkX = (cx + radius) >> 4;
        int minChunkZ = (cz - radius) >> 4;
        int maxChunkZ = (cz + radius) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) continue;

                long key = chunkKey(chunkX, chunkZ);
                snapshots.put(key, world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(false, false, false));
            }
        }
        return snapshots;
    }

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
