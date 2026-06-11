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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;

public class SessionManager {

    /*
     * Singleton – double-checked locking with volatile field.
     * The original non-volatile field was unsafe on the Java memory model:
     * a second thread could see a partially-constructed instance.
     */
    private static volatile SessionManager instance;

    /*
     * Block locations obtained from block.getLocation() always carry
     * yaw=0f / pitch=0f, so Location.equals / hashCode is consistent for
     * them and Set.contains() is O(1) – no manual stream scan needed.
     */
    private final Map<UUID, Set<Location>> treeChopProcessingLocations = new ConcurrentHashMap<>();
    private final Map<String, Set<Location>> leafRemovalRemovedLogs = new ConcurrentHashMap<>();

    /*
     * Reverse index: playerKey → active sessionId.
     * Previously, trackRemovedLogForPlayer and clearAllPlayerSessions iterated
     * every entry in leafRemovalRemovedLogs looking for a playerKey prefix –
     * O(all active sessions) per call.  With this map both operations are O(1).
     */
    private final Map<String, String> playerKeyToSessionId = new ConcurrentHashMap<>();

    private final Set<String> activeLeafRemovalSessions = ConcurrentHashMap.newKeySet();
    private final Set<UUID> leafCheckInProgress = ConcurrentHashMap.newKeySet();

    private SessionManager() {}

    public static SessionManager getInstance() {
        SessionManager result = instance;
        if (result == null) {
            synchronized (SessionManager.class) {
                result = instance;
                if (result == null) {
                    instance = result = new SessionManager();
                }
            }
        }
        return result;
    }

    /**
     * O(1) – uses {@code Set.contains()} instead of the previous
     * {@code stream().anyMatch()} with manual coordinate comparison.
     */
    public boolean isLocationProcessing(UUID playerUUID, Location location) {
        Set<Location> locations = treeChopProcessingLocations.get(playerUUID);
        return locations != null && locations.contains(location);
    }

    public void addTreeChopLocations(UUID playerUUID, Collection<Location> locations) {
        treeChopProcessingLocations
                .computeIfAbsent(playerUUID, k -> ConcurrentHashMap.newKeySet())
                .addAll(locations);
    }

    public void removeTreeChopLocations(UUID playerUUID, Collection<Location> locations) {
        Set<Location> playerLocations = treeChopProcessingLocations.get(playerUUID);
        if (playerLocations != null) {
            playerLocations.removeAll(locations);
            if (playerLocations.isEmpty()) {
                treeChopProcessingLocations.remove(playerUUID);
            }
        }
    }

    public void clearTreeChopSession(UUID playerUUID) {
        treeChopProcessingLocations.remove(playerUUID);
    }

    public boolean hasActiveLeafRemovalSession(String playerKey) {
        return activeLeafRemovalSessions.contains(playerKey);
    }

    /**
     * Start a new leaf removal session.
     *
     * @return the session ID, or {@code null} if the player already has one
     */
    public String startLeafRemovalSession(String playerKey) {
        if (hasActiveLeafRemovalSession(playerKey)) {
            return null;
        }
        String sessionId = playerKey + "_" + System.currentTimeMillis();
        leafRemovalRemovedLogs.put(sessionId, ConcurrentHashMap.newKeySet());
        activeLeafRemovalSessions.add(playerKey);
        playerKeyToSessionId.put(playerKey, sessionId); // populate reverse index
        return sessionId;
    }

    public void trackRemovedLog(String sessionId, Location location) {
        Set<Location> logs = leafRemovalRemovedLogs.get(sessionId);
        if (logs != null) {
            logs.add(location.clone());
        }
    }

    /**
     * O(1) via reverse index.
     * Previously iterated {@code leafRemovalRemovedLogs.entrySet()} searching
     * for entries whose key started with {@code playerKey + "_"} – O(all sessions).
     * This is called for every broken log block, making the old cost significant.
     */
    public void trackRemovedLogForPlayer(String playerKey, Location location) {
        String sessionId = playerKeyToSessionId.get(playerKey);
        if (sessionId != null) {
            Set<Location> logs = leafRemovalRemovedLogs.get(sessionId);
            if (logs != null) {
                logs.add(location.clone());
            }
        }
    }

    public Set<Location> getRemovedLogs(String sessionId) {
        return leafRemovalRemovedLogs.getOrDefault(sessionId, Collections.emptySet());
    }

    /**
     * O(1) via {@code Set.contains()}.
     * Previously used {@code stream().anyMatch()} with manual coordinate comparison.
     */
    public boolean isLogRemoved(String sessionId, Location location) {
        Set<Location> logs = leafRemovalRemovedLogs.get(sessionId);
        return logs != null && logs.contains(location);
    }

    public void endLeafRemovalSession(String sessionId, String playerKey) {
        leafRemovalRemovedLogs.remove(sessionId);
        activeLeafRemovalSessions.remove(playerKey);
        playerKeyToSessionId.remove(playerKey); // clean up reverse index
    }

    public boolean startLeafCheck(UUID uuid) {
        return leafCheckInProgress.add(uuid);
    }

    public void finishLeafCheck(UUID uuid) {
        leafCheckInProgress.remove(uuid);
    }

    public boolean hasAnyActiveSession(UUID playerUUID) {
        return treeChopProcessingLocations.containsKey(playerUUID)
                || hasActiveLeafRemovalSession(playerUUID.toString());
    }

    /**
     * Clears every session belonging to a player.
     *
     * <p>The leaf-removal cleanup now uses the reverse index ({@code playerKeyToSessionId})
     * to locate the player's active session in O(1) instead of scanning all session
     * keys with a prefix match.
     */
    public void clearAllPlayerSessions(UUID playerUUID) {
        clearTreeChopSession(playerUUID);
        finishLeafCheck(playerUUID);

        String playerKey = playerUUID.toString();

        // O(1) lookup via reverse index
        String sessionId = playerKeyToSessionId.get(playerKey);
        if (sessionId != null) {
            endLeafRemovalSession(sessionId, playerKey);
            return;
        }

        // Fallback: defensive linear scan in case the reverse index missed an entry
        // (e.g. sessions started before the index existed during a hot-reload).
        List<String> toRemove = new java.util.ArrayList<>();
        for (String sid : leafRemovalRemovedLogs.keySet()) {
            if (sid.startsWith(playerKey + "_")) {
                toRemove.add(sid);
            }
        }
        toRemove.forEach(sid -> endLeafRemovalSession(sid, playerKey));
    }

    public String getStats() {
        return String.format(
                "TreeChop sessions: %d, LeafRemoval sessions: %d",
                treeChopProcessingLocations.size(), leafRemovalRemovedLogs.size());
    }
}
