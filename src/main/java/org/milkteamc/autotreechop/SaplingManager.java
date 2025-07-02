package org.milkteamc.autotreechop;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SaplingManager {
    private final AutoTreeChop plugin;
    private final Map<Material, Material> saplingMap = new HashMap<>();
    private static final Set<Material> SOIL_BLOCKS = EnumSet.of(
            Material.DIRT,
            Material.GRASS_BLOCK,
            Material.PODZOL,
            Material.COARSE_DIRT,
            Material.MYCELIUM,
            Material.ROOTED_DIRT,
            Material.FARMLAND,
            Material.MOSS_BLOCK,
            Material.MUD,
            Material.MUDDY_MANGROVE_ROOTS
    );

    public SaplingManager(AutoTreeChop plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        saplingMap.clear();
        File file = new File(plugin.getDataFolder(), "saplings.yml");
        if (!file.exists()) {
            plugin.saveResource("saplings.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            Material log = Material.getMaterial(key);
            Material sapling = Material.getMaterial(config.getString(key));
            if (log != null && sapling != null) {
                saplingMap.put(log, sapling);
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save saplings.yml: " + e.getMessage());
        }
    }

    public void plantSapling(Material originalLog, Block brokenBlock) {
        Material sapling = saplingMap.get(originalLog);
        if (sapling == null) {
            return;
        }
        Block below = brokenBlock.getRelative(0, -1, 0);
        if (SaplingManager.SOIL_BLOCKS.contains(below.getType()) && brokenBlock.getType() == Material.AIR) {
            brokenBlock.setType(sapling);
        }
    }
}
