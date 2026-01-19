package org.milkteamc.autotreechop.utils;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.particles.ParticleDisplay;
import com.cryptomorin.xseries.particles.XParticle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.awt.Color;

import static org.milkteamc.autotreechop.AutoTreeChop.HIT_MAX_BLOCK_MESSAGE;
import static org.milkteamc.autotreechop.AutoTreeChop.sendMessage;

public class EffectUtils {


    public static void sendMaxBlockLimitReachedMessage(Player player, Block block) {
        sendMessage(player, HIT_MAX_BLOCK_MESSAGE);
        
        ParticleDisplay.of(XParticle.DUST)
                .withLocation(block.getLocation().add(0.5, 0.5, 0.5))
                .withColor(Color.RED, 1.0f)
                .withCount(50)
                .offset(0.5, 0.5, 0.5)
                .spawn();
    }

    public static void showChopEffect(Player player, Block block) {
        ParticleDisplay.of(XParticle.HAPPY_VILLAGER)
                .withLocation(block.getLocation().add(0.5, 0.5, 0.5))
                .withCount(50)
                .offset(0.5, 0.5, 0.5)
                .spawn();
    }

    public static void showReplantEffect(Player player, Block block) {
        ParticleDisplay.of(XParticle.HAPPY_VILLAGER)
                .withLocation(block.getLocation().add(0.5, 0.5, 0.5))
                .withCount(20)
                .offset(0.3, 0.3, 0.3)
                .spawn();
        
        // Add some bone meal-like particles for extra visual feedback
        if (XParticle.COMPOSTER.isSupported()) {
            ParticleDisplay.of(XParticle.COMPOSTER)
                    .withLocation(block.getLocation().add(0.5, 0.2, 0.5))
                    .withCount(15)
                    .offset(0.2, 0.1, 0.2)
                    .withExtra(0.05)
                    .spawn();
        }
        
        // Green dust particles to simulate plant growth
        ParticleDisplay.of(XParticle.DUST)
                .withLocation(block.getLocation().add(0.5, 0.3, 0.5))
                .withColor(Color.GREEN, 0.8f)
                .withCount(10)
                .offset(0.2, 0.2, 0.2)
                .spawn();
    }

    public static void showLeafRemovalEffect(Player player, Block block) {
        // Brown/orange dust particles to represent decaying leaves
        ParticleDisplay.of(XParticle.DUST)
                .withLocation(block.getLocation().add(0.5, 0.5, 0.5))
                .withColor(new Color(139, 69, 19), 0.8f) // Brown color
                .withCount(15)
                .offset(0.3, 0.3, 0.3)
                .spawn();
        
        // Falling leaf-like block particles
        if (XMaterial.supports(13)) {
            try {
                XMaterial blockMaterial = XMaterial.matchXMaterial(block.getType());
                if (blockMaterial != null && blockMaterial.parseMaterial() != null) {
                    ParticleDisplay.of(XParticle.BLOCK)
                            .withLocation(block.getLocation().add(0.5, 0.8, 0.5))
                            .withBlock(blockMaterial.parseMaterial().createBlockData())
                            .withCount(10)
                            .offset(0.2, 0.1, 0.2)
                            .spawn();
                }
            } catch (NoSuchMethodError | UnsupportedOperationException e) {
            }
        }
    }
}