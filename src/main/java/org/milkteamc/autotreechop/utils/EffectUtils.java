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

    // Shows a green particle effect with growth particles indicating a sapling has been replanted
    public static void showReplantEffect(Player player, Block block) {
        // Show green particles with a slight upward motion to indicate growth/planting
        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, block.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0);

        // Add some bone meal-like particles for extra visual feedback
        player.getWorld().spawnParticle(Particle.COMPOSTER, block.getLocation().add(0.5, 0.2, 0.5), 15, 0.2, 0.1, 0.2, 0.05);

        // Optional: Add some green dust particles to simulate plant growth
        player.getWorld().spawnParticle(Particle.REDSTONE, block.getLocation().add(0.5, 0.3, 0.5), 10, 0.2, 0.2, 0.2, 0, new Particle.DustOptions(Color.GREEN, 0.8f));
    }

    public static void showLeafRemovalEffect(Player player, Block block) {
        // Show brown/orange particles to represent decaying leaves
        player.getWorld().spawnParticle(Particle.REDSTONE, block.getLocation().add(0.5, 0.5, 0.5),
                15, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(Color.fromRGB(139, 69, 19), 0.8f));

        // Add some falling leaf-like particles
        player.getWorld().spawnParticle(Particle.BLOCK_CRACK, block.getLocation().add(0.5, 0.8, 0.5),
                10, 0.2, 0.1, 0.2, 0.1, block.getBlockData());
    }
}