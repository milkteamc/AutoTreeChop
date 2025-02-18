package org.milkteamc.autotreechop.utils;

import de.cubbossa.tinytranslations.Message;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import static org.milkteamc.autotreechop.AutoTreeChop.sendMessage;

public class EffectUtils {

    // Sends a message to the player and shows a red particle effect indicating the block limit has been reached
    public static void sendMaxBlockLimitReachedMessage(Player player, Block block, Message HIT_MAX_BLOCK_MESSAGE) {
        sendMessage(player, HIT_MAX_BLOCK_MESSAGE);
        player.getWorld().spawnParticle(Particle.REDSTONE, block.getLocation().add(0.5, 0.5, 0.5), 50, 0.5, 0.5, 0.5, 0, new Particle.DustOptions(Color.RED, 1));
    }

    // Shows a green particle effect indicating the block has been chopped
    public static void showChopEffect(Player player, Block block) {
        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, block.getLocation().add(0.5, 0.5, 0.5), 50, 0.5, 0.5, 0.5, 0);
    }
}