package org.milkteamc.autotreechop.utils;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
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

    /**
     * Returned by {@link #consumePendingConfirmation}: carries both the reason that
     * triggered the confirmation and the chop parameters that were saved at that time.
     * {@code blockLocation} and {@code tool} are used by {@code /atc confirm} to fire
     * the chop without requiring the player to physically re-break the log.
     */
    public record ChopData(ConfirmReason reason, Location blockLocation, ItemStack tool) {}

    // Internal record — not exposed; callers receive ChopData instead.
    private record PendingConfirmation(long expiryMs, ConfirmReason reason, Location blockLocation, ItemStack tool) {}

    private final AutoTreeChop plugin;

    /** Timestamp (ms) of the last successful ATC chop per player within the current session. */
    private final Map<UUID, Long> lastChopTime = new ConcurrentHashMap<>();

    /**
     * Active confirmation window per player.
     * A single entry per player holds the expiry timestamp, reason, and chop parameters,
     * allowing atomic read-and-remove via {@link ConcurrentHashMap#compute}.
     */
    private final Map<UUID, PendingConfirmation> pendingConfirmations = new ConcurrentHashMap<>();

    /**
     * Expiry timestamp (ms) of the no-leaves grace window.
     * While active, the no-leaves check is suppressed so the player can chop
     * a whole bare trunk without repeated confirmations.
     *
     * <p>Uses {@code idle-timeout} as its duration (not {@code confirmation-window})
     * so the grace period is long enough to cover an entire bare trunk.
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
     * Stores {@code blockLocation} and {@code tool} so that {@code /atc confirm}
     * can dispatch the chop without a second physical block break.
     *
     * @param uuid          the player
     * @param reason        the reason that triggered the confirmation requirement
     * @param blockLocation location of the log that was blocked (cloned by caller)
     * @param tool          the tool held at break time (cloned by caller)
     */
    public void setPendingConfirmation(UUID uuid, ConfirmReason reason, Location blockLocation, ItemStack tool) {
        long expiryMs = System.currentTimeMillis() + plugin.getPluginConfig().getConfirmationWindowSeconds() * 1000L;
        pendingConfirmations.put(uuid, new PendingConfirmation(expiryMs, reason, blockLocation, tool));
    }

    /**
     * Atomically checks whether a confirmation is pending, consumes it if valid,
     * and returns a {@link ChopData} containing the reason and saved chop parameters.
     *
     * <p>The read-expiry-check-and-remove is performed inside a single
     * {@link ConcurrentHashMap#compute} call, eliminating the TOCTOU race that
     * separate {@code get}/{@code remove} calls would introduce.
     *
     * @param uuid the player
     * @return a {@link ChopData} if a valid confirmation was pending, or {@code null}
     *         if nothing was pending or the window had already expired
     */
    public ChopData consumePendingConfirmation(UUID uuid) {
        ChopData[] result = {null};
        pendingConfirmations.compute(uuid, (k, existing) -> {
            if (existing == null) {
                return null; // nothing pending — leave absent
            }
            if (System.currentTimeMillis() > existing.expiryMs()) {
                return null; // expired — remove and return null to caller
            }
            // valid — capture data and remove the entry
            result[0] = new ChopData(existing.reason(), existing.blockLocation(), existing.tool());
            return null;
        });
        return result[0];
    }

    /**
     * Records a successful chop, resets the idle timer, and updates the
     * no-leaves grace window according to whether the chop had nearby leaves.
     *
     * @param uuid            the player
     * @param confirmedReason the reason that was pending when they confirmed;
     *                        pass {@code null} for a normal non-confirmation chop
     * @param hasLeaves       whether the log that was just broken had nearby leaf blocks
     */
    public void recordSuccessfulChop(UUID uuid, ConfirmReason confirmedReason, boolean hasLeaves) {
        lastChopTime.put(uuid, System.currentTimeMillis());
        rejoinPending.remove(uuid);
        pendingConfirmations.remove(uuid);

        if (confirmedReason == ConfirmReason.NO_LEAVES || confirmedReason == ConfirmReason.BOTH) {
            long graceMs = plugin.getPluginConfig().getIdleTimeoutSeconds() * 1000L;
            noLeavesGraceExpiry.put(uuid, System.currentTimeMillis() + graceMs);
        } else if (hasLeaves) {
            noLeavesGraceExpiry.remove(uuid);
        }
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
