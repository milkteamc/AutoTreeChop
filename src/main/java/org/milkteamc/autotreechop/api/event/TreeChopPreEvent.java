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

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after a tree is accepted, before AutoTreeChop starts removing its logs. */
public final class TreeChopPreEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location origin;
    private final Set<Location> plannedLogs;
    private boolean cancelled;

    public TreeChopPreEvent(Player player, Location origin, Set<Location> plannedLogs) {
        this.player = Objects.requireNonNull(player, "player");
        this.origin = Objects.requireNonNull(origin, "origin").clone();
        this.plannedLogs = copyLocations(plannedLogs);
    }

    public Player getPlayer() {
        return player;
    }

    public Location getOrigin() {
        return origin.clone();
    }

    /** Returns detached locations of the logs selected by discovery, before per-block checks. */
    public Set<Location> getPlannedLogs() {
        return copyLocations(plannedLogs);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
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
