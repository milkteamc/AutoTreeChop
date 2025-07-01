package org.milkteamc.autotreechop.hooks;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Simple reflection based hook into Drop2Inventory-Plus so that items
 * broken by this plugin are placed directly in the player's inventory
 * when the plugin is installed.
 */
public class Drop2InventoryHook {
    private final Object api;
    private final Method processMethod;

    public Drop2InventoryHook() {
        Object tmpApi = null;
        Method tmpMethod = null;
        try {
            // Try the modern API class first
            Class<?> clazz = Class.forName("de.jeff_media.drop2inventory.api.Drop2InventoryAPI");
            tmpApi = clazz.getMethod("getInstance").invoke(null);
            tmpMethod = clazz.getMethod("processBlockBreak", Player.class, Block.class);
        } catch (Exception ignored) {
            try {
                // Older plugin versions
                Class<?> clazz = Class.forName("de.jeff_media.drop2inventory.Drop2InventoryAPI");
                tmpApi = clazz.getMethod("getInstance").invoke(null);
                tmpMethod = clazz.getMethod("processBlockBreak", Player.class, Block.class);
            } catch (Exception ignored2) {
                // Plugin not found or API changed
            }
        }
        this.api = tmpApi;
        this.processMethod = tmpMethod;
    }

    /**
     * Attempt to let Drop2Inventory handle the block break. Returns true
     * if the plugin processed the break.
     */
    public boolean processBlock(Player player, Block block) {
        if (api == null || processMethod == null) {
            return false;
        }
        try {
            processMethod.invoke(api, player, block);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
