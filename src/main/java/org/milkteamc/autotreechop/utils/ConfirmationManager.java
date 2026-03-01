package org.milkteamc.autotreechop.utils;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.milkteamc.autotreechop.AutoTreeChop;

public class ConfirmationManager {

    /** The reason(s) why a confirmation is currently required. */
    public enum ConfirmReason {
        /** Player is idle or just rejoined the server. */
        IDLE_OR_REJOIN,
        /** The target log has no leaf blocks nearby. */
        NO_LEAVES,
        /** Both IDLE_OR_REJOIN and NO_LEAVES apply simultaneously. */
        BOTH
    }

    private final AutoTreeChop plugin;

    /** Timestamp (ms) of the last successful ATC chop per player within the current session. */
    private final Map<UUID, Long> lastChopTime = new ConcurrentHashMap<>();

    /**
     * Expiry timestamp (ms) of an active confirmation window.
     * Present only while the player is in the "pending confirmation" state.
     */
    private final Map<UUID, Long> pendingConfirmationExpiry = new ConcurrentHashMap<>();

    /**
     * The reason that opened the currently pending confirmation window.
     * Cleared together with {@link #pendingConfirmationExpiry}.
     */
    private final Map<UUID, ConfirmReason> pendingReason = new ConcurrentHashMap<>();

    /**
     * Expiry timestamp (ms) of the no-leaves grace window.
     * While active, the no-leaves check is suppressed so the player can chop
     * a whole bare trunk without repeated confirmations.
     */
    private final Map<UUID, Long> noLeavesGraceExpiry = new ConcurrentHashMap<>();

    /**
     * Players who joined (or rejoined) while ATC was enabled.
     * Cleared once they successfully confirm.
     */
    private final Set<UUID> rejoinPending = ConcurrentHashMap.newKeySet();

    public ConfirmationManager(AutoTreeChop plugin) {
        this.plugin = plugin;
    }

    /**
     * Marks a player as requiring confirmation because they rejoined the server.
     * Call this from {@code PlayerJoinListener} when ATC is already enabled.
     * Has no effect if {@code enable-idle-confirmation} is disabled in config.
     */
    public void markRejoin(UUID uuid) {
        if (plugin.getPluginConfig().isIdleConfirmationEnabled()) {
            rejoinPending.add(uuid);
        }
    }

    /**
     * Opens a time-limited confirmation window for the given reason.
     * The window duration is driven by {@code Config#getConfirmationWindowSeconds()}.
     *
     * @param uuid   the player
     * @param reason the reason that triggered the confirmation requirement
     */
    public void setPendingConfirmation(UUID uuid, ConfirmReason reason) {
        long windowMs = plugin.getPluginConfig().getConfirmationWindowSeconds() * 1000L;
        pendingConfirmationExpiry.put(uuid, System.currentTimeMillis() + windowMs);
        pendingReason.put(uuid, reason);
    }

    /**
     * Atomically checks whether a confirmation is pending, and if so consumes
     * it — removing the expiry and reason in a single operation — and returns
     * the reason.
     *
     * Use this instead of calling {@link #isConfirmationPending} followed by
     * {@link #getPendingReason}, which has a TOCTOU race: the window could expire
     * in the nanoseconds between the two calls, causing {@code getPendingReason}
     * to return {@code null} even though {@code isConfirmationPending} returned
     * {@code true}.
     *
     * @param uuid the player
     * @return the {@link ConfirmReason} that was pending, or {@code null} if no
     *         confirmation was pending or the window had already expired
     */
    public ConfirmReason consumePendingConfirmation(UUID uuid) {
        Long expiry = pendingConfirmationExpiry.get(uuid);
        if (expiry == null) return null;

        if (System.currentTimeMillis() > expiry) {
            // Window expired — clean up so the next chop triggers a fresh warning
            pendingConfirmationExpiry.remove(uuid);
            pendingReason.remove(uuid);
            return null;
        }

        // Atomically consume both entries
        pendingConfirmationExpiry.remove(uuid);
        return pendingReason.remove(uuid);
    }

    /**
     * Records a successful chop confirmation, resets the idle timer, and clears
     * all idle/rejoin pending state.
     *
     * <p>If the confirmed reason involved NO_LEAVES (or BOTH), a no-leaves grace
     * window is opened so the player can finish the bare trunk uninterrupted.
     * Otherwise any stale no-leaves grace is cleared (they moved to a leafy tree).
     *
     * @param uuid            the player
     * @param confirmedReason the reason that was pending when they confirmed;
     *                        pass {@code null} for a normal non-confirmation chop
     */
    public void recordSuccessfulChop(UUID uuid, ConfirmReason confirmedReason) {
        lastChopTime.put(uuid, System.currentTimeMillis());
        rejoinPending.remove(uuid);
        // pendingConfirmation is already consumed by consumePendingConfirmation before
        // this is called, but defensively clear in case the path skipped that step.
        pendingConfirmationExpiry.remove(uuid);
        pendingReason.remove(uuid);

        if (confirmedReason == ConfirmReason.NO_LEAVES || confirmedReason == ConfirmReason.BOTH) {
            // Grant a grace window so the entire bare trunk can be chopped without re-confirming
            long windowMs = plugin.getPluginConfig().getConfirmationWindowSeconds() * 1000L;
            noLeavesGraceExpiry.put(uuid, System.currentTimeMillis() + windowMs);
        } else {
            // Player moved to a leafy tree — stale no-leaves grace is irrelevant
            noLeavesGraceExpiry.remove(uuid);
        }
    }

    /**
     * Removes all tracking state for a player.
     * Call this on quit and whenever ATC is disabled for the player.
     */
    public void clearPlayer(UUID uuid) {
        lastChopTime.remove(uuid);
        pendingConfirmationExpiry.remove(uuid);
        pendingReason.remove(uuid);
        noLeavesGraceExpiry.remove(uuid);
        rejoinPending.remove(uuid);
    }

    /**
     * Determines whether confirmation is required before ATC may process the
     * player's next chop, and if so returns <em>why</em>.
     *
     * <p>Each trigger is independently gated by its own config flag, so either
     * or both can be disabled without affecting the other.
     *
     * @param uuid      the player
     * @param hasLeaves whether the log block being broken has leaf blocks nearby
     * @return the {@link ConfirmReason} if confirmation is required,
     *         or {@code null} if the player may chop freely
     */
    public ConfirmReason getConfirmationReason(UUID uuid, boolean hasLeaves) {
        boolean idleOrRejoin = plugin.getPluginConfig().isIdleConfirmationEnabled() && isIdleOrRejoin(uuid);
        boolean noLeaves =
                plugin.getPluginConfig().isNoLeavesConfirmationEnabled() && !hasLeaves && !isNoLeavesGraceActive(uuid);

        if (idleOrRejoin && noLeaves) return ConfirmReason.BOTH;
        if (idleOrRejoin) return ConfirmReason.IDLE_OR_REJOIN;
        if (noLeaves) return ConfirmReason.NO_LEAVES;
        return null;
    }

    /**
     * Returns {@code true} if the player is currently inside an active confirmation
     * window (i.e. they were shown a warning and have not yet confirmed or timed out).
     *
     * Prefer {@link #consumePendingConfirmation} over calling this followed by
     * {@link #getPendingReason} to avoid a TOCTOU race condition.
     */
    public boolean isConfirmationPending(UUID uuid) {
        Long expiry = pendingConfirmationExpiry.get(uuid);
        if (expiry == null) return false;

        if (System.currentTimeMillis() > expiry) {
            // Window expired — clean up so the next chop triggers a fresh warning
            pendingConfirmationExpiry.remove(uuid);
            pendingReason.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * Returns {@code true} if the idle-or-rejoin trigger applies.
     *
     * <p>Players who just enabled ATC this session (no prior chop recorded) are
     * <em>not</em> considered idle — we don't nag them on the very first chop.
     */
    private boolean isIdleOrRejoin(UUID uuid) {
        if (rejoinPending.contains(uuid)) return true;

        Long last = lastChopTime.get(uuid);
        if (last == null) return false; // First chop this session — don't nag

        long idleTimeoutMs = plugin.getPluginConfig().getIdleTimeoutSeconds() * 1000L;
        return (System.currentTimeMillis() - last) > idleTimeoutMs;
    }

    /**
     * Returns {@code true} if the no-leaves grace window is currently active,
     * suppressing the no-leaves confirmation check.
     */
    private boolean isNoLeavesGraceActive(UUID uuid) {
        Long expiry = noLeavesGraceExpiry.get(uuid);
        if (expiry == null) return false;

        if (System.currentTimeMillis() > expiry) {
            noLeavesGraceExpiry.remove(uuid);
            return false;
        }
        return true;
    }
}
