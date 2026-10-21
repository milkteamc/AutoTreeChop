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
 
package org.milkteamc.autotreechop.api.event;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired once a started chopping batch finishes, including when no logs were removed. */
public final class TreeChopPostEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location origin;
    private final Set<Location> plannedLogs;
    private final Map<Location, Material> removedLogs;

    public TreeChopPostEvent(
            Player player, Location origin, Set<Location> plannedLogs, Map<Location, Material> removedLogs) {
        this.player = Objects.requireNonNull(player, "player");
        this.origin = Objects.requireNonNull(origin, "origin").clone();
        this.plannedLogs = copyLocations(plannedLogs);
        Objects.requireNonNull(removedLogs, "removedLogs");
        this.removedLogs = new LinkedHashMap<>();
        removedLogs.forEach((location, material) -> this.removedLogs.put(
                Objects.requireNonNull(location, "location").clone(), Objects.requireNonNull(material, "material")));
    }

    public Player getPlayer() {
        return player;
    }

    public Location getOrigin() {
        return origin.clone();
    }

    /** Returns detached locations of the logs selected by discovery. */
    public Set<Location> getPlannedLogs() {
        return copyLocations(plannedLogs);
    }

    /** Returns actual removed log locations and their original materials; may be empty. */
    public Map<Location, Material> getRemovedLogs() {
        Map<Location, Material> copy = new LinkedHashMap<>();
        removedLogs.forEach((location, material) -> copy.put(location.clone(), material));
        return copy;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    private static Set<Location> copyLocations(Set<Location> locations) {
        Objects.requireNonNull(locations, "plannedLogs");
        Set<Location> copy = new LinkedHashSet<>();
        for (Location location : locations)
            copy.add(Objects.requireNonNull(location, "location").clone());
        return copy;
    }
}
