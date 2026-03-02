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

    private record PendingConfirmation(long expiryMs, ConfirmReason reason) {}

    private final AutoTreeChop plugin;

    /** Timestamp (ms) of the last successful ATC chop per player within the current session. */
    private final Map<UUID, Long> lastChopTime = new ConcurrentHashMap<>();

    /**
     * Active confirmation window per player.
     * A single entry per player holds both the expiry timestamp and the reason,
     * allowing atomic read-and-remove via {@link ConcurrentHashMap#compute}.
     */
    private final Map<UUID, PendingConfirmation> pendingConfirmations = new ConcurrentHashMap<>();

    /**
     * Expiry timestamp (ms) of the no-leaves grace window.
     * While active, the no-leaves check is suppressed so the player can chop
     * a whole bare trunk without repeated confirmations.
     *
     * <p>Uses {@code idle-timeout} as its duration (not {@code confirmation-window})
     * so the grace period is long enough to cover an entire bare trunk. A 10-second
     * {@code confirmation-window} would expire mid-chop on any trunk taller than a
     * few logs, forcing repeated confirmations. {@code idle-timeout} (default 300 s)
     * gives the player a full session's worth of uninterrupted chopping before the
     * safety prompt reappears.
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
        long expiryMs = System.currentTimeMillis() + plugin.getPluginConfig().getConfirmationWindowSeconds() * 1000L;
        pendingConfirmations.put(uuid, new PendingConfirmation(expiryMs, reason));
    }

    /**
     * Atomically checks whether a confirmation is pending, consumes it if valid,
     * and returns the reason — all in a single {@link ConcurrentHashMap#compute}
     * call so no other thread can observe a half-cleared state.
     *
     * <p>Using two separate maps with individual {@code get}/{@code remove} calls
     * introduced a TOCTOU race: the expiry entry could be removed by a concurrent
     * call between the check and the reason lookup, causing the reason to be
     * {@code null} even though the expiry check had passed. Merging both fields
     * into {@link PendingConfirmation} and operating on a single map entry
     * eliminates that window.
     *
     * @param uuid the player
     * @return the {@link ConfirmReason} that was pending, or {@code null} if no
     *         confirmation was pending or the window had already expired
     */
    public ConfirmReason consumePendingConfirmation(UUID uuid) {
        // compute() holds the bucket lock for the duration of the lambda, making
        // the read-expiry-check-and-remove sequence fully atomic.
        ConfirmReason[] result = {null};
        pendingConfirmations.compute(uuid, (k, existing) -> {
            if (existing == null) {
                return null; // nothing pending — leave absent
            }
            if (System.currentTimeMillis() > existing.expiryMs()) {
                return null; // expired — remove and return null to caller
            }
            result[0] = existing.reason(); // valid — capture reason and remove
            return null;
        });
        return result[0];
    }

    /**
     * Records a successful chop, resets the idle timer, and updates the
     * no-leaves grace window according to whether the chop had nearby leaves.
     *
     * <p>Grace window rules:
     * <ul>
     *   <li>If {@code confirmedReason} is NO_LEAVES or BOTH → open a new grace window
     *       so the player can finish the bare trunk uninterrupted.</li>
     *   <li>If {@code hasLeaves} is {@code true} → close any existing grace window
     *       (player has moved to a leafy tree).</li>
     *   <li>Otherwise (bare log, grace already active, or confirmation disabled) →
     *       leave the grace window as-is. Clearing it here would end the grace mid-trunk
     *       every time the player incidentally breaks a non-leaf, non-confirmed block.</li>
     * </ul>
     *
     * @param uuid            the player
     * @param confirmedReason the reason that was pending when they confirmed;
     *                        pass {@code null} for a normal non-confirmation chop
     * @param hasLeaves       whether the log that was just broken had nearby leaf blocks;
     *                        pass {@code false} when the leaf state is unknown (e.g. the
     *                        player confirmed via {@code /atc confirm} or by re-breaking)
     */
    public void recordSuccessfulChop(UUID uuid, ConfirmReason confirmedReason, boolean hasLeaves) {
        lastChopTime.put(uuid, System.currentTimeMillis());
        rejoinPending.remove(uuid);
        // pendingConfirmation is already consumed by consumePendingConfirmation before
        // this is called, but defensively clear in case the path skipped that step.
        pendingConfirmations.remove(uuid);

        if (confirmedReason == ConfirmReason.NO_LEAVES || confirmedReason == ConfirmReason.BOTH) {
            // Use idle-timeout for the grace window duration, not confirmation-window.
            // confirmation-window (default 10 s) is too short to chop an entire bare
            // trunk; idle-timeout (default 300 s) matches the natural session length.
            long graceMs = plugin.getPluginConfig().getIdleTimeoutSeconds() * 1000L;
            noLeavesGraceExpiry.put(uuid, System.currentTimeMillis() + graceMs);
        } else if (hasLeaves) {
            // Player demonstrably moved to a leafy tree — stale no-leaves grace is
            // now irrelevant and can be cleared.
            //
            // Critically, we only clear when hasLeaves is *true*. If the player breaks a
            // bare log while the grace window is active (hasLeaves=false, confirmedReason=null),
            // clearing here would kill the grace mid-trunk, forcing re-confirmation on
            // every subsequent bare log for the rest of the trunk.
            noLeavesGraceExpiry.remove(uuid);
        }
        // hasLeaves=false, confirmedReason=null (or IDLE_OR_REJOIN): grace is unchanged.
    }

    /**
     * Removes all tracking state for a player.
     * Call this on quit and whenever ATC is disabled for the player.
     */
    public void clearPlayer(UUID uuid) {
        lastChopTime.remove(uuid);
        pendingConfirmations.remove(uuid);
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
