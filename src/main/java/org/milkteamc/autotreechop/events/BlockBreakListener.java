package org.milkteamc.autotreechop.events;

import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.PlayerConfig;
import org.milkteamc.autotreechop.utils.BlockDiscoveryUtils;
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

        if (BlockDiscoveryUtils.isLeafBlock(block.getType(), plugin.getPluginConfig())) {
            UUID uuid = player.getUniqueId();
            if (SessionManager.getInstance().hasActiveLeafRemovalSession(uuid.toString())) {
                return;
            }
        }

        if (SessionManager.getInstance().isLocationProcessing(playerUUID, location)) {
            return;
        }

        Material material = block.getType();

        if (playerConfig.isAutoTreeChopEnabled() && BlockDiscoveryUtils.isLog(material, plugin.getPluginConfig())) {

            if (plugin.getCooldownManager().isInCooldown(playerUUID)) {
                long remainingCooldown = plugin.getCooldownManager().getRemainingCooldown(playerUUID);
                AutoTreeChop.sendMessage(
                        player,
                        AutoTreeChop.STILL_IN_COOLDOWN_MESSAGE,
                        Placeholder.parsed("cooldown_time", String.valueOf(remainingCooldown)));
                return;
            }

            if (!PermissionUtils.hasVipBlock(player, playerConfig, plugin.getPluginConfig())) {
                if (playerConfig.getDailyBlocksBroken()
                        >= plugin.getPluginConfig().getMaxBlocksPerDay()) {
                    EffectUtils.sendMaxBlockLimitReachedMessage(player, block);
                    return;
                }
            }

            if (!PermissionUtils.hasVipUses(player, playerConfig, plugin.getPluginConfig())
                    && playerConfig.getDailyUses() >= plugin.getPluginConfig().getMaxUsesPerDay()) {
                AutoTreeChop.sendMessage(player, AutoTreeChop.HIT_MAX_USAGE_MESSAGE);
                return;
            }

            if (plugin.getPluginConfig().isVisualEffect()) {
                EffectUtils.showChopEffect(player, block);
            }

            event.setCancelled(true);

            // Create ProtectionHooks with all protection plugins' state
            ProtectionHooks hooks = new ProtectionHooks(
                    plugin.isWorldGuardEnabled(), plugin.getWorldGuardHook(),
                    plugin.isResidenceEnabled(), plugin.getResidenceHook(),
                    plugin.isGriefPreventionEnabled(), plugin.getGriefPreventionHook(),
                    plugin.isLandsEnabled(), plugin.getLandsHook());

            plugin.getTreeChopUtils()
                    .chopTree(
                            block,
                            player,
                            plugin.getPluginConfig().isStopChoppingIfNotConnected(),
                            tool,
                            location,
                            plugin.getPluginConfig(),
                            playerConfig,
                            hooks);
        }
    }
}
