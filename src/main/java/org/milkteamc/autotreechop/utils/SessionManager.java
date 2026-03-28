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
 
package org.milkteamc.autotreechop.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;

public class SessionManager {

    private static SessionManager instance;
    private final Map<UUID, Set<Location>> treeChopProcessingLocations = new ConcurrentHashMap<>();
    private final Map<String, Set<Location>> leafRemovalRemovedLogs = new ConcurrentHashMap<>();
    private final Set<String> activeLeafRemovalSessions = ConcurrentHashMap.newKeySet();

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    /**
     * Check if a location is currently being processed in a TreeChop session
     */
    public boolean isLocationProcessing(UUID playerUUID, Location location) {
        Set<Location> locations = treeChopProcessingLocations.get(playerUUID);
        if (locations == null) return false;

        return locations.stream()
                .anyMatch(loc -> loc.getBlockX() == location.getBlockX()
                        && loc.getBlockY() == location.getBlockY()
                        && loc.getBlockZ() == location.getBlockZ()
                        && Objects.equals(loc.getWorld(), location.getWorld()));
    }

    /**
     * Add locations to TreeChop processing set
     */
    public void addTreeChopLocations(UUID playerUUID, Collection<Location> locations) {
        treeChopProcessingLocations
                .computeIfAbsent(playerUUID, k -> ConcurrentHashMap.newKeySet())
                .addAll(locations);
    }

    /**
     * Remove locations from TreeChop processing set
     */
    public void removeTreeChopLocations(UUID playerUUID, Collection<Location> locations) {
        Set<Location> playerLocations = treeChopProcessingLocations.get(playerUUID);
        if (playerLocations != null) {
            playerLocations.removeAll(locations);
            if (playerLocations.isEmpty()) {
                treeChopProcessingLocations.remove(playerUUID);
            }
        }
    }

    /**
     * Clear all TreeChop locations for a player
     */
    public void clearTreeChopSession(UUID playerUUID) {
        treeChopProcessingLocations.remove(playerUUID);
    }

    /**
     * Check if player has an active leaf removal session
     */
    public boolean hasActiveLeafRemovalSession(String playerKey) {
        return activeLeafRemovalSessions.contains(playerKey);
    }

    /**
     * Start a new leaf removal session
     *
     * @return session ID
     */
    public String startLeafRemovalSession(String playerKey) {
        if (hasActiveLeafRemovalSession(playerKey)) {
            return null; // Already has active session
        }

        String sessionId = playerKey + "_" + System.currentTimeMillis();
        leafRemovalRemovedLogs.put(sessionId, ConcurrentHashMap.newKeySet());
        activeLeafRemovalSessions.add(playerKey);
        return sessionId;
    }

    /**
     * Track a removed log in a leaf removal session
     */
    public void trackRemovedLog(String sessionId, Location location) {
        Set<Location> logs = leafRemovalRemovedLogs.get(sessionId);
        if (logs != null) {
            logs.add(location.clone());
        }
    }

    /**
     * Track a removed log for all active sessions of a player
     */
    public void trackRemovedLogForPlayer(String playerKey, Location location) {
        for (Map.Entry<String, Set<Location>> entry : leafRemovalRemovedLogs.entrySet()) {
            if (entry.getKey().startsWith(playerKey + "_")) {
                entry.getValue().add(location.clone());
            }
        }
    }

    /**
     * Get removed logs for a session
     */
    public Set<Location> getRemovedLogs(String sessionId) {
        return leafRemovalRemovedLogs.getOrDefault(sessionId, Collections.emptySet());
    }

    /**
     * Check if a location was removed in this session
     */
    public boolean isLogRemoved(String sessionId, Location location) {
        Set<Location> logs = leafRemovalRemovedLogs.get(sessionId);
        if (logs == null) return false;

        return logs.stream()
                .anyMatch(loc -> loc.getBlockX() == location.getBlockX()
                        && loc.getBlockY() == location.getBlockY()
                        && loc.getBlockZ() == location.getBlockZ()
                        && Objects.equals(loc.getWorld(), location.getWorld()));
    }

    /**
     * End a leaf removal session and cleanup
     */
    public void endLeafRemovalSession(String sessionId, String playerKey) {
        leafRemovalRemovedLogs.remove(sessionId);
        activeLeafRemovalSessions.remove(playerKey);
    }

    /**
     * Check if player has any active session (TreeChop or LeafRemoval)
     */
    public boolean hasAnyActiveSession(UUID playerUUID) {
        return treeChopProcessingLocations.containsKey(playerUUID)
                || hasActiveLeafRemovalSession(playerUUID.toString());
    }

    /**
     * Clear all sessions for a player (useful for cleanup on logout)
     */
    public void clearAllPlayerSessions(UUID playerUUID) {
        clearTreeChopSession(playerUUID);

        String playerKey = playerUUID.toString();
        // Find and remove all leaf removal sessions for this player
        List<String> toRemove = new ArrayList<>();
        for (String sessionId : leafRemovalRemovedLogs.keySet()) {
            if (sessionId.startsWith(playerKey + "_")) {
                toRemove.add(sessionId);
            }
        }
        toRemove.forEach(sessionId -> endLeafRemovalSession(sessionId, playerKey));
    }

    /**
     * Get statistics for debugging
     */
    public String getStats() {
        return String.format(
                "TreeChop sessions: %d, LeafRemoval sessions: %d",
                treeChopProcessingLocations.size(), leafRemovalRemovedLogs.size());
    }
}
