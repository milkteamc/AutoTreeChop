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
 
package org.milkteamc.autotreechop.configuration;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Immutable group settings published together with the rest of the configuration. */
public final class GroupPolicies {
    private final Policy defaultPolicy;
    private final Policy legacyVipPolicy;
    private final List<Group> groups;

    private GroupPolicies(Policy defaultPolicy, Policy legacyVipPolicy, List<Group> groups) {
        this.defaultPolicy = defaultPolicy;
        this.legacyVipPolicy = legacyVipPolicy;
        this.groups = List.copyOf(groups);
    }

    public static GroupPolicies from(YamlDocument document) {
        boolean globalLimit = document.getBoolean("chopping.limit-usage");
        Section defaults = document.getSection("groups.default");
        Policy defaultPolicy = read("default", defaults, defaults, globalLimit);
        List<Group> groups = new ArrayList<>();
        for (Object key : document.getSection("groups").getKeys()) {
            String name = key.toString();
            if (name.equals("default")) continue;
            Section section = document.getSection("groups." + name);
            groups.add(new Group(section.getInt("priority", 0), read(name, section, defaults, globalLimit)));
        }
        groups.sort(Comparator.comparingInt(Group::priority).reversed().thenComparing(group -> group.policy()
                .group()));
        return new GroupPolicies(
                defaultPolicy, read("vip", document.getSection("groups.vip"), defaults, globalLimit), groups);
    }

    private static Policy read(String name, Section section, Section defaults, boolean globalLimit) {
        return new Policy(
                name,
                globalLimit && section.getBoolean("limit-usage", defaults.getBoolean("limit-usage", true)),
                section.getInt("max-uses-per-day", defaults.getInt("max-uses-per-day")),
                section.getInt("max-blocks-per-day", defaults.getInt("max-blocks-per-day")),
                section.getInt("cooldown-seconds", defaults.getInt("cooldown-seconds")));
    }

    public Policy resolve(Predicate<String> groupPermission, boolean legacyVip) {
        for (Group group : groups) {
            if (groupPermission.test("autotreechop.group." + group.policy().group())) return group.policy();
        }
        return legacyVip ? legacyVipPolicy : defaultPolicy;
    }

    private record Group(int priority, Policy policy) {}

    public record Policy(
            String group, boolean limitUsage, int maxUsesPerDay, int maxBlocksPerDay, int cooldownSeconds) {
        public boolean canUse(int dailyUses) {
            return !limitUsage || dailyUses < maxUsesPerDay;
        }

        public boolean canBreakBlocks(int dailyBlocks, int count) {
            if (count < 0) throw new IllegalArgumentException("count must be nonnegative");
            return !limitUsage || (long) dailyBlocks + count <= maxBlocksPerDay;
        }
    }
}
