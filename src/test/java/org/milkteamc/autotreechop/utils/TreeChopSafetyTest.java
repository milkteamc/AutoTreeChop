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
 
package org.milkteamc.autotreechop.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.*;
import org.milkteamc.autotreechop.*;
import org.milkteamc.autotreechop.api.event.TreeChopPostEvent;
import org.milkteamc.autotreechop.api.event.TreeChopPreEvent;
import org.milkteamc.autotreechop.database.DataManager;
import org.milkteamc.autotreechop.hooks.HookManager;
import org.milkteamc.autotreechop.hooks.SignProtectionHook;
import org.mockito.MockedConstruction;

class TreeChopSafetyTest {
    private final AutoTreeChop plugin = mock(AutoTreeChop.class);
    private final DataManager manager = mock(DataManager.class);
    private final Player player = mock(Player.class);
    private final PlayerConfig data = mock(PlayerConfig.class);
    private final Config config = mock(Config.class);
    private final Block block = mock(Block.class);
    private final World world = mock(World.class);
    private final Location location = new Location(world, 0, 64, 0);
    private final UUID uuid = UUID.randomUUID();
    private final ProtectionCheckUtils.ProtectionHooks hooks =
            new ProtectionCheckUtils.ProtectionHooks(false, null, false, null, false, null, false, null);
    private MockedConstruction<AsyncTaskScheduler> schedulers;
    private MockedConstruction<BatchProcessor> batches;
    private TreeChopUtils utils;

    @BeforeEach
    void setup() {
        when(data.getPreferences()).thenReturn(PlayerPreferences.DEFAULTS);
        when(config.getActivationMode()).thenReturn(org.milkteamc.autotreechop.configuration.ActivationMode.COMMAND);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(plugin.getDataManager()).thenReturn(manager);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        when(server.getPluginManager()).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getConfirmationManager()).thenReturn(mock(ConfirmationManager.class));
        Block soil = mock(Block.class);
        when(soil.getType()).thenReturn(Material.DIRT);
        when(world.getBlockAt(location.clone().subtract(0, 1, 0))).thenReturn(soil);
        when(plugin.getCooldownManager()).thenReturn(mock(CooldownManager.class));
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("autotreechop.use")).thenReturn(true);
        when(manager.getPlayerConfig(uuid)).thenReturn(data);
        when(data.isAutoTreeChopEnabled()).thenReturn(true);
        when(config.getMaxTreeSize()).thenReturn(100);
        when(config.resolvePolicy(player))
                .thenReturn(new org.milkteamc.autotreechop.configuration.GroupPolicies.Policy(
                        "default", false, 50, 500, 5));
        when(block.getLocation()).thenReturn(location);
        when(block.getType()).thenReturn(Material.OAK_LOG);
        when(config.getLogTypes()).thenReturn(Set.of(Material.OAK_LOG));
        when(world.getBlockAt(location)).thenReturn(block);
        schedulers = mockConstruction(AsyncTaskScheduler.class);
        batches = mockConstruction(BatchProcessor.class);
        utils = new TreeChopUtils(plugin);
    }

    @AfterEach
    void cleanup() {
        schedulers.close();
        batches.close();
        SessionManager.getInstance().clearAllPlayerSessions(uuid);
    }

    private void validate(ItemStack tool) throws Exception {
        validate(tool, true, null);
    }

    private void validate(ItemStack tool, boolean hasLeaves, ConfirmationManager.ConfirmReason reason)
            throws Exception {
        Method method = TreeChopUtils.class.getDeclaredMethod(
                "validateAndExecuteChop",
                Set.class,
                Block.class,
                Player.class,
                ItemStack.class,
                Config.class,
                PlayerConfig.class,
                ProtectionCheckUtils.ProtectionHooks.class,
                boolean.class,
                ConfirmationManager.ConfirmReason.class);
        method.setAccessible(true);
        method.invoke(utils, Set.of(location), block, player, tool, config, data, hooks, hasLeaves, reason);
    }

    @Test
    void offlinePlayerCannotResumeDiscovery() throws Exception {
        when(player.isOnline()).thenReturn(false);
        validate(null);
        verify(data, never()).incrementDailyUses();
        verifyNoInteractions(batches.constructed().get(0));
    }

    @Test
    void oldLoginCannotResumeAfterRejoin() throws Exception {
        when(manager.getPlayerConfig(uuid)).thenReturn(mock(PlayerConfig.class));
        validate(null);
        verify(data, never()).incrementDailyUses();
        verifyNoInteractions(batches.constructed().get(0));
    }

    @Test
    void switchedToolCannotStartChopping() throws Exception {
        ItemStack original = mock(ItemStack.class);
        when(player.getInventory().getItemInMainHand()).thenReturn(mock(ItemStack.class));
        validate(original);
        verify(data, never()).incrementDailyUses();
        verifyNoInteractions(batches.constructed().get(0));
    }

    @Test
    void releasingSneakDuringDiscoveryPreventsCombinedModeFromStarting() throws Exception {
        when(config.getActivationMode())
                .thenReturn(org.milkteamc.autotreechop.configuration.ActivationMode.COMMAND_AND_SNEAK);
        when(player.isSneaking()).thenReturn(false);
        validate(null);
        verifyNoInteractions(batches.constructed().get(0));
    }

    @Test
    void releasingSneakBeforeTheFirstQueuedBlockPreventsRemoval() throws Exception {
        when(config.getActivationMode())
                .thenReturn(org.milkteamc.autotreechop.configuration.ActivationMode.COMMAND_AND_SNEAK);
        when(player.isSneaking()).thenReturn(true);
        validate(null);
        var invocation = mockingDetails(batches.constructed().get(0))
                .getInvocations()
                .iterator()
                .next();
        BiConsumer<Location, Integer> processor = invocation.getArgument(3);
        when(player.isSneaking()).thenReturn(false);
        processor.accept(location, 0);
        verify(block, never()).breakNaturally();
        verify(data, never()).incrementDailyUses();
    }

    @Test
    @SuppressWarnings("unchecked")
    void queuedBlockWorkStopsAfterQuit() throws Exception {
        validate(null);
        var invocation = mockingDetails(batches.constructed().get(0))
                .getInvocations()
                .iterator()
                .next();
        BiConsumer<Location, Integer> processor = invocation.getArgument(3);
        when(player.isOnline()).thenReturn(false);
        clearInvocations(block, data);
        processor.accept(location, 0);
        verifyNoInteractions(block, data);
    }

    private ItemStack prepareTool() {
        ItemStack tool = mock(ItemStack.class);
        Material material = mock(Material.class);
        Damageable meta = mock(Damageable.class);
        when(material.getMaxDurability()).thenReturn((short) 1000);
        when(tool.getType()).thenReturn(material);
        when(tool.getAmount()).thenReturn(1);
        when(tool.getItemMeta()).thenReturn(meta);
        when(tool.clone()).thenReturn(tool);
        when(player.getInventory().getItemInMainHand()).thenReturn(tool);
        when(config.isToolDamage()).thenReturn(true);
        when(config.getToolDamageDecrease()).thenReturn(1);
        when(config.getLogTypes()).thenReturn(Set.of(Material.OAK_LOG));
        when(block.getType()).thenReturn(Material.OAK_LOG);
        return tool;
    }

    private BiConsumer<Location, Integer> blockProcessor() {
        return mockingDetails(batches.constructed().get(0))
                .getInvocations()
                .iterator()
                .next()
                .getArgument(3);
    }

    @Test
    void failedBreakDoesNotCostDurabilityOrCount() throws Exception {
        ItemStack tool = prepareTool();
        when(config.isRecordMinecraftStatistics()).thenReturn(true);
        when(block.breakNaturally()).thenReturn(false);
        validate(tool);
        blockProcessor().accept(location, 0);
        verify((Damageable) tool.getItemMeta(), never()).setDamage(anyInt());
        verify(data, never()).incrementDailyBlocksBroken();
        verify(data, never()).incrementDailyUses();
        verify(player, never()).incrementStatistic(any(Statistic.class), any(Material.class));
    }

    @Test
    void successfulBreakChargesBeforeNextBatchAndSwapStopsRemainingWork() throws Exception {
        ItemStack tool = prepareTool();
        when(block.breakNaturally()).thenReturn(true);
        validate(tool);
        BiConsumer<Location, Integer> processor = blockProcessor();
        processor.accept(location, 0);
        verify((Damageable) tool.getItemMeta()).setDamage(1);
        verify(data).incrementDailyBlocksBroken();
        when(player.getInventory().getItemInMainHand()).thenReturn(mock(ItemStack.class));
        processor.accept(location, 1);
        when(player.getInventory().getItemInMainHand()).thenReturn(tool);
        processor.accept(location, 2);
        verify(block, times(1)).breakNaturally();
        verify((Damageable) tool.getItemMeta(), times(1)).setDamage(anyInt());
        verify(player, never()).incrementStatistic(any(Statistic.class), any(Material.class));
    }

    @Test
    void cancelledEventDoesNotCostDurabilityOrCount() throws Exception {
        ItemStack tool = prepareTool();
        when(config.isRecordMinecraftStatistics()).thenReturn(true);
        when(config.isCallBlockBreakEvent()).thenReturn(true);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        org.bukkit.plugin.PluginManager plugins = mock(org.bukkit.plugin.PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
        doAnswer(invocation -> {
                    ((org.bukkit.event.block.BlockBreakEvent) invocation.getArgument(0)).setCancelled(true);
                    return null;
                })
                .when(plugins)
                .callEvent(any(BlockBreakEvent.class));
        validate(tool);
        blockProcessor().accept(location, 0);
        verify(block, never()).breakNaturally();
        verify((Damageable) tool.getItemMeta(), never()).setDamage(anyInt());
        verify(data, never()).incrementDailyBlocksBroken();
        verify(data, never()).incrementDailyUses();
        verify(player, never()).incrementStatistic(any(Statistic.class), any(Material.class));
    }

    @Test
    void cancelledPreEventDoesNotStartOrConsumeAConfirmedChop() throws Exception {
        org.bukkit.plugin.PluginManager plugins = plugin.getServer().getPluginManager();
        doAnswer(invocation -> {
                    ((TreeChopPreEvent) invocation.getArgument(0)).setCancelled(true);
                    return null;
                })
                .when(plugins)
                .callEvent(any(TreeChopPreEvent.class));

        validate(null, true, ConfirmationManager.ConfirmReason.FLOATING);

        verify(plugins).callEvent(any(TreeChopPreEvent.class));
        verify(plugins, never()).callEvent(any(TreeChopPostEvent.class));
        verifyNoInteractions(batches.constructed().get(0));
        verify(plugin.getConfirmationManager(), never()).recordSuccessfulChop(any(), any(), anyBoolean());
        verify(data, never()).incrementDailyUses();
        assertFalse(SessionManager.getInstance().hasActiveTreeChopSession(uuid));
    }

    @Test
    void postEventReportsOnlyLogsActuallyRemovedAndKeepsTheirMaterial() throws Exception {
        when(block.breakNaturally()).thenReturn(false, true);
        validate(null);
        BiConsumer<Location, Integer> processor = blockProcessor();
        processor.accept(location, 0);
        processor.accept(location, 1);
        Runnable completion = mockingDetails(batches.constructed().get(0))
                .getInvocations()
                .iterator()
                .next()
                .getArgument(4);
        completion.run();

        org.mockito.ArgumentCaptor<TreeChopPostEvent> captured =
                org.mockito.ArgumentCaptor.forClass(TreeChopPostEvent.class);
        verify(plugin.getServer().getPluginManager()).callEvent(captured.capture());
        TreeChopPostEvent post = captured.getValue();
        assertEquals(Set.of(location), post.getPlannedLogs());
        assertEquals(java.util.Map.of(location, Material.OAK_LOG), post.getRemovedLogs());
        post.getOrigin().add(10, 0, 0);
        post.getRemovedLogs().keySet().iterator().next().add(10, 0, 0);
        assertEquals(location, post.getOrigin());
        assertEquals(java.util.Map.of(location, Material.OAK_LOG), post.getRemovedLogs());
    }

    @Test
    void postEventStillFiresWhenEveryBlockBreakIsCancelled() throws Exception {
        when(config.isCallBlockBreakEvent()).thenReturn(true);
        org.bukkit.plugin.PluginManager plugins = plugin.getServer().getPluginManager();
        doAnswer(invocation -> {
                    ((BlockBreakEvent) invocation.getArgument(0)).setCancelled(true);
                    return null;
                })
                .when(plugins)
                .callEvent(any(BlockBreakEvent.class));
        validate(null);
        blockProcessor().accept(location, 0);
        Runnable completion = mockingDetails(batches.constructed().get(0))
                .getInvocations()
                .iterator()
                .next()
                .getArgument(4);
        completion.run();

        org.mockito.ArgumentCaptor<TreeChopPostEvent> captured =
                org.mockito.ArgumentCaptor.forClass(TreeChopPostEvent.class);
        verify(plugins).callEvent(captured.capture());
        assertTrue(captured.getValue().getRemovedLogs().isEmpty());
        verify(data, never()).incrementDailyUses();
    }

    @Test
    void enabledMinecraftStatisticsCountEachSuccessfulLogOnly() throws Exception {
        when(config.isRecordMinecraftStatistics()).thenReturn(true);
        when(block.breakNaturally()).thenReturn(false, true);
        validate(null);
        BiConsumer<Location, Integer> processor = blockProcessor();
        processor.accept(location, 0);
        verify(player, never()).incrementStatistic(any(Statistic.class), any(Material.class));
        processor.accept(location, 1);
        verify(player).incrementStatistic(Statistic.MINE_BLOCK, Material.OAK_LOG);
        verify(data).incrementDailyBlocksBroken();
    }

    @Test
    void noDropChopStillRecordsMinecraftStatistic() throws Exception {
        when(config.isRecordMinecraftStatistics()).thenReturn(true);
        when(config.isCallBlockBreakEvent()).thenReturn(true);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        org.bukkit.plugin.PluginManager plugins = mock(org.bukkit.plugin.PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
        doAnswer(invocation -> {
                    ((org.bukkit.event.block.BlockBreakEvent) invocation.getArgument(0)).setDropItems(false);
                    return null;
                })
                .when(plugins)
                .callEvent(any(BlockBreakEvent.class));
        validate(null);
        blockProcessor().accept(location, 0);
        verify(block).setType(Material.AIR, false);
        verify(player).incrementStatistic(Statistic.MINE_BLOCK, Material.OAK_LOG);
    }

    @Test
    void protectedSignRejectsWholeTreeBeforeBatchStarts() throws Exception {
        HookManager manager = mock(HookManager.class);
        SignProtectionHook signHook = mock(SignProtectionHook.class);
        when(plugin.getHookManager()).thenReturn(manager);
        when(manager.getSignProtectionHook()).thenReturn(signHook);
        when(signHook.inspect(Set.of(location))).thenReturn(SignProtectionHook.Result.PROTECTED);
        validate(null);
        verifyNoInteractions(batches.constructed().get(0));
        verify(data, never()).incrementDailyUses();
    }

    @Test
    void liteModeAllowsChoppingWithoutUsePermissionOrSignProtectionHook() throws Exception {
        when(config.isLiteMode()).thenReturn(true);
        when(player.hasPermission("autotreechop.use")).thenReturn(false);
        HookManager manager = mock(HookManager.class);
        SignProtectionHook signHook = mock(SignProtectionHook.class);
        when(plugin.getHookManager()).thenReturn(manager);
        when(manager.getSignProtectionHook()).thenReturn(signHook);

        validate(null);

        assertNotNull(blockProcessor());
        verifyNoInteractions(signHook);
    }

    @Test
    void signAddedByBreakListenerIsCheckedAgainBeforeRemoval() throws Exception {
        HookManager manager = mock(HookManager.class);
        SignProtectionHook signHook = mock(SignProtectionHook.class);
        when(plugin.getHookManager()).thenReturn(manager);
        when(manager.getSignProtectionHook()).thenReturn(signHook);
        when(signHook.inspect(Set.of(location))).thenReturn(SignProtectionHook.Result.SAFE);
        when(signHook.inspect(location))
                .thenReturn(SignProtectionHook.Result.SAFE, SignProtectionHook.Result.PROTECTED);
        when(config.isCallBlockBreakEvent()).thenReturn(true);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        org.bukkit.plugin.PluginManager plugins = mock(org.bukkit.plugin.PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
        validate(null);
        blockProcessor().accept(location, 0);
        verify(plugins).callEvent(any(BlockBreakEvent.class));
        verify(block, never()).breakNaturally();
        verify(data, never()).incrementDailyUses();
    }

    @Test
    void staleLeafCompletionCannotClearNewLoginSession() {
        SessionManager sessions = SessionManager.getInstance();
        String key = uuid.toString();
        String oldSession = sessions.startLeafRemovalSession(key);
        sessions.clearAllPlayerSessions(uuid);
        String currentSession = sessions.startLeafRemovalSession(key);
        sessions.endLeafRemovalSession(oldSession, key);
        assertTrue(sessions.hasActiveLeafRemovalSession(key));
        sessions.endLeafRemovalSession(currentSession, key);
        assertFalse(sessions.hasActiveLeafRemovalSession(key));
    }

    @Test
    void unbreakableToolRemainsUndamaged() throws Exception {
        ItemStack tool = prepareTool();
        when(((Damageable) tool.getItemMeta()).isUnbreakable()).thenReturn(true);
        when(block.breakNaturally()).thenReturn(true);
        validate(tool);
        blockProcessor().accept(location, 0);
        verify((Damageable) tool.getItemMeta(), never()).setDamage(anyInt());
        verify(data).incrementDailyBlocksBroken();
        verify(data).incrementDailyUses();
    }

    @Test
    void successfulBlocksCountOnlyOneUse() throws Exception {
        ItemStack tool = prepareTool();
        when(block.breakNaturally()).thenReturn(true);
        validate(tool);
        blockProcessor().accept(location, 0);
        blockProcessor().accept(location, 1);
        verify(data).incrementDailyUses();
        verify(data, times(2)).incrementDailyBlocksBroken();
    }

    @Test
    void cooldownIsCheckedEvenWhenBypassingBlockListener() throws Exception {
        when(plugin.getCooldownManager().isInCooldown(uuid)).thenReturn(true);
        validate(null);
        verifyNoInteractions(batches.constructed().get(0));
        verify(data, never()).incrementDailyUses();
    }

    @Test
    void liteModeIgnoresCooldownLeftFromFullMode() throws Exception {
        when(config.isLiteMode()).thenReturn(true);
        when(plugin.getCooldownManager().isInCooldown(uuid)).thenReturn(true);
        validate(null);
        verify(plugin.getCooldownManager(), never()).isInCooldown(uuid);
        verify(batches.constructed().get(0)).processBatch(anyList(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void playerTeleportToDifferentRegionDoesNotAccessInventory() throws Exception {
        ItemStack tool = prepareTool();
        validate(tool);
        try (var access = mockStatic(RegionAccess.class)) {
            access.when(() -> RegionAccess.owns(location)).thenReturn(true);
            access.when(() -> RegionAccess.owns(player)).thenReturn(false);
            clearInvocations(player.getInventory(), block);
            blockProcessor().accept(location, 0);
            verifyNoInteractions(player.getInventory(), block);
        }
    }

    @Test
    void incompleteCanopyAbortsBeforeChangingBlocks() throws Exception {
        when(config.isLeafRemovalEnabled()).thenReturn(true);
        when(player.hasPermission("autotreechop.leaves")).thenReturn(true);
        try (var snapshots = mockStatic(BlockSnapshotCreator.class)) {
            validate(null);
            verifyNoInteractions(batches.constructed().get(0));
            verify(block, never()).breakNaturally();
            assertFalse(SessionManager.getInstance().hasActiveTreeChopSession(uuid));
        }
    }

    @Test
    void teleportDuringDiscoveryReleasesTheTreeSession() throws Exception {
        SessionManager.getInstance().addTreeChopLocations(uuid, Set.of(location));
        try (var access = mockStatic(RegionAccess.class)) {
            access.when(() -> RegionAccess.owns(player)).thenReturn(false);
            validate(null);
            assertFalse(SessionManager.getInstance().hasActiveTreeChopSession(uuid));
        }
    }

    @Test
    void floatingTreeRequiresConfirmationBeforeAnyBatchAndOverridesNoLeavesPrompt() throws Exception {
        when(world.getBlockAt(location.clone().subtract(0, 1, 0)).getType()).thenReturn(Material.AIR);
        SessionManager.getInstance().addTreeChopLocations(uuid, Set.of(location));
        validate(null, false, null);
        verify(plugin.getConfirmationManager())
                .setPendingConfirmation(uuid, ConfirmationManager.ConfirmReason.FLOATING, location, null, false);
        verifyNoInteractions(batches.constructed().get(0));
        verify(data, never()).incrementDailyUses();
        assertFalse(SessionManager.getInstance().hasActiveTreeChopSession(uuid));
    }

    @Test
    void confirmingFloatingTreeStartsBatchWithoutGrantingAnotherTreePermission() throws Exception {
        when(world.getBlockAt(location.clone().subtract(0, 1, 0)).getType()).thenReturn(Material.AIR);
        validate(null, true, ConfirmationManager.ConfirmReason.FLOATING);
        assertNotNull(blockProcessor());
        verify(plugin.getConfirmationManager(), never())
                .setPendingConfirmation(any(), any(), any(), any(), anyBoolean());
        SessionManager.getInstance().clearTreeChopSession(uuid);
        clearInvocations(batches.constructed().get(0));
        validate(null);
        verifyNoInteractions(batches.constructed().get(0));
        verify(plugin.getConfirmationManager())
                .setPendingConfirmation(uuid, ConfirmationManager.ConfirmReason.FLOATING, location, null, true);
    }

    @Test
    void formerlyGroundedTreeRequiresNewConfirmationWhenSupportDisappears() throws Exception {
        when(world.getBlockAt(location.clone().subtract(0, 1, 0)).getType()).thenReturn(Material.AIR);
        validate(null, true, ConfirmationManager.ConfirmReason.IDLE_OR_REJOIN);
        verifyNoInteractions(batches.constructed().get(0));
        verify(plugin.getConfirmationManager())
                .setPendingConfirmation(uuid, ConfirmationManager.ConfirmReason.FLOATING, location, null, true);
    }

    @Test
    void physicalRetryOfFloatingTreeConfirmsLikeNoLeavesRetry() throws Exception {
        when(world.getBlockAt(location.clone().subtract(0, 1, 0)).getType()).thenReturn(Material.AIR);
        when(plugin.getPluginConfig()).thenReturn(config);
        when(config.getConfirmationWindowSeconds()).thenReturn(30);
        ConfirmationManager confirmations = new ConfirmationManager(plugin);
        when(plugin.getConfirmationManager()).thenReturn(confirmations);
        validate(null, false, null);
        verifyNoInteractions(batches.constructed().get(0));
        validate(null, false, null);
        assertNotNull(blockProcessor());
        assertNull(confirmations.consumePendingConfirmation(uuid));
    }

    @Test
    void floatingTreeDoesNotReplantEvenIfSoilAppearsDuringTheBatch() throws Exception {
        ItemStack tool = prepareTool();
        Block support = world.getBlockAt(location.clone().subtract(0, 1, 0));
        when(support.getType()).thenReturn(Material.AIR);
        when(block.breakNaturally()).thenReturn(true);
        when(config.isAutoReplantEnabled()).thenReturn(true);
        when(player.hasPermission("autotreechop.replant")).thenReturn(true);
        validate(tool, true, ConfirmationManager.ConfirmReason.FLOATING);
        when(support.getType()).thenReturn(Material.DIRT);
        blockProcessor().accept(location, 0);
        Runnable completion = mockingDetails(batches.constructed().get(0))
                .getInvocations()
                .iterator()
                .next()
                .getArgument(4);
        completion.run();
        verify(data).incrementDailyBlocksBroken();
        verify(config, never()).getSaplingForLog(any());
    }

    @Test
    void disablingDuringDiscoveryCannotStartANewBatchOrConfirmation() throws Exception {
        when(data.isAutoTreeChopEnabled()).thenReturn(false);
        SessionManager.getInstance().addTreeChopLocations(uuid, Set.of(location));
        validate(null);
        verifyNoInteractions(batches.constructed().get(0));
        verify(plugin.getConfirmationManager(), never()).recordSuccessfulChop(any(), any(), anyBoolean());
        assertFalse(SessionManager.getInstance().hasActiveTreeChopSession(uuid));
    }

    @Test
    void suspiciousStructureRequiresConfirmationEvenWithLeavesAndAfterAnIdleConfirmation() throws Exception {
        when(config.isPlayerStructureConfirmationEnabled()).thenReturn(true);
        try (var detector = mockStatic(TreeStructureDetector.class)) {
            detector.when(() -> TreeStructureDetector.inspect(Set.of(location), config))
                    .thenReturn(TreeStructureDetector.Result.SUSPICIOUS);
            validate(null, true, ConfirmationManager.ConfirmReason.IDLE_OR_REJOIN);
            verify(plugin.getConfirmationManager())
                    .setPendingConfirmation(
                            uuid, ConfirmationManager.ConfirmReason.PLAYER_STRUCTURE, location, null, true);
            verifyNoInteractions(batches.constructed().get(0));
            verify(block, never()).breakNaturally();
            verify(data, never()).incrementDailyUses();
        }
    }

    @Test
    void structureRetryConfirmsOnceAndDoesNotAuthorizeTheNextStructure() throws Exception {
        when(config.isPlayerStructureConfirmationEnabled()).thenReturn(true);
        when(plugin.getPluginConfig()).thenReturn(config);
        when(config.getConfirmationWindowSeconds()).thenReturn(30);
        when(plugin.getConfirmationManager()).thenReturn(new ConfirmationManager(plugin));
        try (var detector = mockStatic(TreeStructureDetector.class)) {
            detector.when(() -> TreeStructureDetector.inspect(Set.of(location), config))
                    .thenReturn(TreeStructureDetector.Result.SUSPICIOUS);
            validate(null);
            verifyNoInteractions(batches.constructed().get(0));
            validate(null);
            assertNotNull(blockProcessor());
            SessionManager.getInstance().clearTreeChopSession(uuid);
            clearInvocations(batches.constructed().get(0));
            validate(null);
            verifyNoInteractions(batches.constructed().get(0));
            assertEquals(
                    ConfirmationManager.ConfirmReason.PLAYER_STRUCTURE,
                    plugin.getConfirmationManager()
                            .consumePendingConfirmation(uuid)
                            .reason());
        }
    }

    @Test
    void floatingStructureCanBeConfirmedWithoutAlternatingWarnings() throws Exception {
        when(config.isPlayerStructureConfirmationEnabled()).thenReturn(true);
        when(world.getBlockAt(location.clone().subtract(0, 1, 0)).getType()).thenReturn(Material.AIR);
        try (var detector = mockStatic(TreeStructureDetector.class)) {
            detector.when(() -> TreeStructureDetector.inspect(Set.of(location), config))
                    .thenReturn(TreeStructureDetector.Result.SUSPICIOUS);
            validate(null, true, ConfirmationManager.ConfirmReason.FLOATING);
            verify(plugin.getConfirmationManager())
                    .setPendingConfirmation(
                            uuid, ConfirmationManager.ConfirmReason.FLOATING_STRUCTURE, location, null, true);
            verifyNoInteractions(batches.constructed().get(0));
            validate(null, true, ConfirmationManager.ConfirmReason.FLOATING_STRUCTURE);
            assertNotNull(blockProcessor());
        }
    }

    @Test
    void incompleteStructureScanStopsEvenWhenPreviouslyConfirmed() throws Exception {
        when(config.isPlayerStructureConfirmationEnabled()).thenReturn(true);
        try (var detector = mockStatic(TreeStructureDetector.class)) {
            detector.when(() -> TreeStructureDetector.inspect(Set.of(location), config))
                    .thenReturn(TreeStructureDetector.Result.INCOMPLETE);
            validate(null, true, ConfirmationManager.ConfirmReason.PLAYER_STRUCTURE);
            verifyNoInteractions(batches.constructed().get(0));
            verify(block, never()).breakNaturally();
        }
    }

    @Test
    void disabledStructureCheckDoesNotInspectNeighbors() throws Exception {
        try (var detector = mockStatic(TreeStructureDetector.class)) {
            validate(null);
            assertNotNull(blockProcessor());
            detector.verifyNoInteractions();
        }
    }

    @Test
    void disabledEntryDoesNotCaptureOrScheduleTreeDiscovery() {
        when(data.isAutoTreeChopEnabled()).thenReturn(false);
        utils.chopTree(block, player, true, null, location, config, data, hooks);
        verifyNoInteractions(
                block, schedulers.constructed().get(0), batches.constructed().get(0));
        assertFalse(SessionManager.getInstance().hasActiveTreeChopSession(uuid));
    }
}
