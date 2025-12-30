package org.milkteamc.autotreechop.utils;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import org.bukkit.Location;
import org.milkteamc.autotreechop.AutoTreeChop;

public class AsyncTaskScheduler {

    private final AutoTreeChop plugin;
    private final TaskScheduler scheduler;
    private final boolean isFolia;

    public AsyncTaskScheduler(AutoTreeChop plugin) {
        this.plugin = plugin;
        this.scheduler = UniversalScheduler.getScheduler(plugin);
        this.isFolia = AutoTreeChop.isFolia();
    }

    /**
     * Run task on GLOBAL scheduler (no block access)
     * Use this ONLY for non-world operations
     */
    public void runTask(Runnable task) {
        scheduler.runTask(task);
    }

    /**
     * Run task on REGION scheduler at specific location (can access blocks)
     * Use this for ANY world/block operations
     */
    public void runTaskAtLocation(Location location, Runnable task) {
        scheduler.runTask(location, task);
    }

    /**
     * Run task asynchronously (no block access)
     */
    public void runTaskAsync(Runnable task) {
        scheduler.runTaskAsynchronously(task);
    }

    /**
     * Run task later on GLOBAL scheduler (no block access)
     * Use this ONLY for non-world operations
     */
    public void runTaskLater(Runnable task, long delayTicks) {
        scheduler.runTaskLater(task, delayTicks);
    }

    /**
     * Run task later on REGION scheduler at specific location (can access blocks)
     * Use this for ANY world/block operations with delay
     */
    public void runTaskLaterAtLocation(Location location, Runnable task, long delayTicks) {
        scheduler.runTaskLater(location, task, delayTicks);
    }

    /**
     * Schedule a delayed task at a specific location (for leaf removal and replanting)
     */
    public void scheduleDelayed(Location location, Runnable task, long delayTicks) {
        if (delayTicks > 0) {
            runTaskLaterAtLocation(location, task, delayTicks);
        } else {
            runTaskAtLocation(location, task);
        }
    }
}