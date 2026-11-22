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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.PlayerConfig;
import org.milkteamc.autotreechop.configuration.ConfigSchema;
import org.milkteamc.autotreechop.database.DataManager;
import org.milkteamc.autotreechop.database.DatabaseManager;
import org.milkteamc.autotreechop.utils.AsyncTaskScheduler;
import org.milkteamc.autotreechop.utils.CooldownManager;

/**
 * With chopping.require-tool enabled, breaking a log without a tool must fall back to a vanilla
 * break. Previously the event was cancelled before the tool check in chopTree(), so the log could
 * not be broken at all.
 */
class BlockBreakListenerRequireToolTest {
    @TempDir
    Path temp;

    private final AutoTreeChop plugin = mock(AutoTreeChop.class);
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final UUID uuid = UUID.randomUUID();
    private final PlayerConfig data =
            new PlayerConfig(uuid, new DatabaseManager.PlayerData(uuid, true, 0, 0, LocalDate.now()));
    private final DataManager manager = mock(DataManager.class);
    private final World world = mock(World.class);
    private final Block block = mock(Block.class);
    private Config config;

    @BeforeEach
    void setup() {
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getResource("config.yml")).thenAnswer(call -> getClass().getResourceAsStream("/config.yml"));
        config = new Config(plugin);
        when(plugin.getPluginConfig()).thenReturn(config);
        when(plugin.getDataManager()).thenReturn(manager);
        when(plugin.getCooldownManager()).thenReturn(mock(CooldownManager.class));
        when(manager.getPlayerConfig(uuid)).thenReturn(data);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.hasPermission("autotreechop.use")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(block.getType()).thenReturn(Material.OAK_LOG);
        when(block.getWorld()).thenReturn(world);
        when(block.getLocation()).thenReturn(new Location(world, 0, 64, 0));
    }

    private void requireTool(boolean value) throws Exception {
        var yaml = ConfigSchema.parse(Files.readString(temp.resolve("config.yml")));
        yaml.set("chopping.require-tool", value);
        Files.writeString(temp.resolve("config.yml"), yaml.dump());
        config.load();
    }

    private void hold(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(inventory.getItemInMainHand()).thenReturn(item);
    }

    private BlockBreakEvent breakLog() {
        var event = new BlockBreakEvent(block, player);
        try (var schedulers = mockConstruction(AsyncTaskScheduler.class)) {
            new BlockBreakListener(plugin).onBlockBreak(event);
        }
        return event;
    }

    @ParameterizedTest
    @EnumSource(
            value = Material.class,
            names = {"AIR", "DIRT", "OAK_PLANKS", "STICK"})
    void requireToolWithoutToolLeavesVanillaBreakAlone(Material held) throws Exception {
        requireTool(true);
        hold(held);
        assertFalse(breakLog().isCancelled());
    }

    @Test
    void requireToolWithToolStillHandsOverToAutoTreeChop() throws Exception {
        requireTool(true);
        hold(Material.IRON_AXE);
        assertTrue(breakLog().isCancelled());
    }

    @Test
    void withoutRequireToolBareHandStillHandsOverToAutoTreeChop() throws Exception {
        requireTool(false);
        hold(Material.AIR);
        assertTrue(breakLog().isCancelled());
    }
}
