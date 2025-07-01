package org.milkteamc.autotreechop.hooks;

import org.bukkit.block.Block;

/**
 * Simple helper to interact with mcMMO's block tracking system.
 * mcMMO marks player placed blocks using metadata. We treat blocks
 * with that metadata as not natural.
 */
public class McMMOHook {
    private static final String[] METADATA_KEYS = {"mcMMOPlacedBlock", "mcMMO:blockPlaced"};

    /**
     * Determine if a block is naturally generated.
     *
     * @param block The block to test
     * @return true if the block is not marked as player placed
     */
    public boolean isNatural(Block block) {
        for (String key : METADATA_KEYS) {
            if (block.hasMetadata(key)) {
                return false;
            }
        }
        return true;
    }
}
