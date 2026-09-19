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
 
package org.milkteamc.autotreechop;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.milkteamc.autotreechop.configuration.ConfigLoadException;
import org.milkteamc.autotreechop.configuration.ConfigSchema;
import org.milkteamc.autotreechop.configuration.GroupPolicies.Policy;
import org.milkteamc.autotreechop.utils.PermissionUtils;

class GroupPolicyTest {
    @TempDir
    Path temp;

    private final Player player = mock(Player.class);
    private final Set<String> granted = new HashSet<>();
    private Config config;

    @BeforeEach
    void setup() {
        var plugin = mock(AutoTreeChop.class);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getResource("config.yml")).thenAnswer(call -> getClass().getResourceAsStream("/config.yml"));
        when(player.isPermissionSet(anyString())).thenAnswer(call -> granted.contains(call.getArgument(0)));
        when(player.hasPermission(anyString())).thenAnswer(call -> granted.contains(call.getArgument(0)));
        config = new Config(plugin);
    }

    private void reload(String contents) throws Exception {
        Files.writeString(temp.resolve("config.yml"), contents);
        config.load();
    }

    @Test
    void highestPriorityWinsAsAWholeAndMissingFieldsInheritDefault() throws Exception {
        reload("""
                groups:
                  default:
                    max-uses-per-day: 14
                    max-blocks-per-day: 140
                    cooldown-seconds: 7
                  donor:
                    priority: 10
                    max-uses-per-day: 90
                    cooldown-seconds: 1
                  builder:
                    priority: 20
                    max-blocks-per-day: 800
                """);
        granted.addAll(Set.of("autotreechop.group.donor", "autotreechop.group.builder", "autotreechop.vip"));
        assertEquals(new Policy("builder", true, 14, 800, 7), config.resolvePolicy(player));
        assertFalse(
                ConfigSchema.parse(Files.readString(temp.resolve("config.yml")))
                        .contains("groups.builder.max-uses-per-day"),
                "Inherited fields must remain inheritable after reload");
    }

    @Test
    void equalPriorityUsesNameOrderRegardlessOfYamlOrder() throws Exception {
        reload("groups: {zebra: {priority: 10}, alpha: {priority: 10}}");
        granted.addAll(Set.of("autotreechop.group.zebra", "autotreechop.group.alpha"));
        assertEquals("alpha", config.resolvePolicy(player).group());
        reload("groups: {alpha: {priority: 10}, zebra: {priority: 10}}");
        assertEquals("alpha", config.resolvePolicy(player).group());
    }

    @Test
    void permissionChangesAreAppliedWithoutReloadOrResettingUsage() throws Exception {
        reload("groups: {donor: {max-uses-per-day: 100}}");
        var data = mock(PlayerConfig.class);
        when(data.getDailyUses()).thenReturn(50);
        assertFalse(PermissionUtils.canUse(player, data, config));
        granted.add("autotreechop.group.donor");
        assertTrue(PermissionUtils.canUse(player, data, config));
        granted.clear();
        assertFalse(PermissionUtils.canUse(player, data, config));
        verify(data, never()).incrementDailyUses();
    }

    @Test
    void explicitDenialNeverSelectsAGroup() throws Exception {
        reload("groups: {donor: {limit-usage: false}}");
        granted.add("autotreechop.group.donor");
        when(player.hasPermission("autotreechop.group.donor")).thenReturn(false);
        assertEquals("default", config.resolvePolicy(player).group());
    }

    @Test
    void opDefaultGrantDoesNotSelectCustomGroupsButKeepsLegacyVip() throws Exception {
        reload("groups: {restricted: {priority: 1000, max-uses-per-day: 0}}");
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.isOp()).thenReturn(true);
        assertEquals(new Policy("vip", false, 100, 1000, 2), config.resolvePolicy(player));
        granted.add("autotreechop.group.restricted");
        assertEquals("restricted", config.resolvePolicy(player).group());
        assertFalse(config.resolvePolicy(player).canUse(0));
    }

    @Test
    void invalidReloadKeepsThePreviousGroupSnapshotAndValidReloadCanRemoveGroups() throws Exception {
        reload("groups: {builder: {max-uses-per-day: 80, cooldown-seconds: 1}}");
        granted.add("autotreechop.group.builder");
        Policy previous = config.resolvePolicy(player);
        assertThrows(ConfigLoadException.class, () -> reload("groups: {builder: {cooldown-seconds: -1}}"));
        assertSame(previous, config.resolvePolicy(player));
        reload("groups: {builder: {max-uses-per-day: 12, cooldown-seconds: 3}}");
        assertEquals(new Policy("builder", true, 12, 500, 3), config.resolvePolicy(player));
        reload("groups: {default: {max-uses-per-day: 8}}");
        assertEquals(new Policy("default", true, 8, 500, 5), config.resolvePolicy(player));
    }

    @Test
    void defaultLimitSwitchIsInheritedByCustomGroupsButCanBeOverridden() throws Exception {
        reload("groups: {default: {limit-usage: false}, donor: {}, restricted: {limit-usage: true, priority: 1}}");
        assertFalse(config.resolvePolicy(player).limitUsage());
        granted.add("autotreechop.group.donor");
        assertFalse(config.resolvePolicy(player).limitUsage());
        granted.add("autotreechop.group.restricted");
        assertTrue(config.resolvePolicy(player).limitUsage());
    }

    @Test
    void zeroQuotasBlockUsageAndUnlimitedDoesNotRemoveCooldown() throws Exception {
        reload("groups: {donor: {max-uses-per-day: 0, max-blocks-per-day: 0, cooldown-seconds: 9}}");
        granted.add("autotreechop.group.donor");
        Policy limited = config.resolvePolicy(player);
        assertFalse(limited.canUse(0));
        assertFalse(limited.canBreakBlocks(0, 1));
        assertTrue(limited.canBreakBlocks(0, 0));
        reload("groups: {donor: {limit-usage: false, cooldown-seconds: 9}}");
        Policy unlimited = config.resolvePolicy(player);
        assertTrue(unlimited.canUse(Integer.MAX_VALUE));
        assertTrue(unlimited.canBreakBlocks(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(9, unlimited.cooldownSeconds());
        assertThrows(IllegalArgumentException.class, () -> unlimited.canBreakBlocks(0, -1));
    }

    @Test
    void blockBudgetDoesNotOverflow() throws Exception {
        reload("groups: {default: {max-blocks-per-day: 2147483647}}");
        Policy policy = config.resolvePolicy(player);
        assertTrue(policy.canBreakBlocks(Integer.MAX_VALUE - 1, 1));
        assertFalse(policy.canBreakBlocks(Integer.MAX_VALUE, 1));
    }

    @Test
    void legacyGlobalOverrideStillMakesExplicitGroupsUnlimited() throws Exception {
        reload("chopping: {limit-usage: false}\ngroups: {restricted: {limit-usage: true, max-uses-per-day: 0}}");
        granted.add("autotreechop.group.restricted");
        assertEquals("restricted", config.resolvePolicy(player).group());
        assertTrue(config.resolvePolicy(player).canUse(Integer.MAX_VALUE));
        assertEquals(5, config.resolvePolicy(player).cooldownSeconds());
    }

    @Test
    void explicitVipPermissionAndLegacyVipPermissionUseTheSamePreset() {
        Policy ordinary = config.resolvePolicy(player);
        assertEquals(new Policy("default", true, 50, 500, 5), ordinary);
        granted.add("autotreechop.vip");
        Policy legacy = config.resolvePolicy(player);
        granted.clear();
        granted.add("autotreechop.group.vip");
        assertEquals(legacy, config.resolvePolicy(player));
    }

    @Test
    void deprecatedHelpersKeepLegacySemanticsEvenWithAnExplicitGroup() throws Exception {
        reload("groups: {restricted: {max-uses-per-day: 0}}");
        granted.addAll(Set.of("autotreechop.vip", "autotreechop.group.restricted"));
        var data = mock(PlayerConfig.class);
        assertTrue(PermissionUtils.hasVipUses(player, data, config));
        assertTrue(PermissionUtils.hasVipBlock(player, data, config));
        assertFalse(PermissionUtils.canUse(player, data, config));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "false",
                "[]",
                "{priority: -1}",
                "{priority: 1.5}",
                "{priority: 2147483648}",
                "{limit-usage: 'false'}",
                "{cooldown-seconds: -1}",
                "{max-uses-per-day: null}",
                "{max-blocks-per-day: unlimited}"
            })
    void invalidCustomGroupIsRejectedWithoutReplacingTheFile(String value) throws Exception {
        String contents = "groups: {donor: " + value + "}";
        assertThrows(ConfigLoadException.class, () -> reload(contents));
        assertEquals(contents, Files.readString(temp.resolve("config.yml")));
        assertEquals("default", config.resolvePolicy(player).group());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Donor", "donor.member", "donor*", "", "donor member"})
    void invalidGroupNamesAreRejected(String name) {
        assertThrows(ConfigLoadException.class, () -> reload("groups: {'" + name + "': {}}"));
    }
}
