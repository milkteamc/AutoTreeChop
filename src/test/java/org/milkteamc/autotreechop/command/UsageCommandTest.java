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
 
package org.milkteamc.autotreechop.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import org.milkteamc.autotreechop.MessageKeys;
import org.milkteamc.autotreechop.PlayerConfig;
import org.milkteamc.autotreechop.configuration.GroupPolicies.Policy;
import org.milkteamc.autotreechop.database.DataManager;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;

class UsageCommandTest {
    @Test
    void displaysTheResolvedGroupBudgetAndUnlimitedUsage() {
        var plugin = mock(AutoTreeChop.class);
        var config = mock(Config.class);
        var player = mock(Player.class);
        var actor = mock(BukkitCommandActor.class);
        var data = mock(DataManager.class);
        var playerData = mock(PlayerConfig.class);
        var uuid = UUID.randomUUID();
        when(actor.isPlayer()).thenReturn(true);
        when(actor.asPlayer()).thenReturn(player);
        when(player.getUniqueId()).thenReturn(uuid);
        when(plugin.getDataManager()).thenReturn(data);
        when(data.getPlayerConfig(uuid)).thenReturn(playerData);
        when(playerData.getDailyUses()).thenReturn(7);
        when(playerData.getDailyBlocksBroken()).thenReturn(80);
        List<Component> messages = new ArrayList<>();
        try (var api = mockStatic(AutoTreeChop.class, call -> {
            assertEquals(player, call.getArgument(0));
            String template = call.getArgument(1).equals(MessageKeys.USAGE)
                    ? "<current_uses>/<max_uses>"
                    : "<current_blocks>/<max_blocks>";
            TagResolver[] resolvers = (TagResolver[]) call.getRawArguments()[2];
            messages.add(MiniMessage.miniMessage().deserialize(template, resolvers));
            return null;
        })) {
            UsageCommand command = new UsageCommand(plugin, config);
            when(config.resolvePolicy(player)).thenReturn(new Policy("builder", true, 14, 800, 7));
            command.usage(actor);
            assertEquals(List.of(Component.text("7/14"), Component.text("80/800")), messages);
            messages.clear();
            when(config.resolvePolicy(player)).thenReturn(new Policy("donor", false, 0, 0, 1));
            command.usage(actor);
            assertEquals(List.of(Component.text("7/∞"), Component.text("80/∞")), messages);
        }
    }
}
