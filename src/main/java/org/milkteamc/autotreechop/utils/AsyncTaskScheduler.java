package org.milkteamc.autotreechop.utils;

import org.bukkit.Location;
import org.milkteamc.autotreechop.AutoTreeChop;
import com.github.Anon8281.universalScheduler.UniversalScheduler;

public class AsyncTaskScheduler {

    private final AutoTreeChop plugin;
    private final boolean isFolia;

    public AsyncTaskScheduler(AutoTreeChop plugin) {
        this.plugin = plugin;
        this.isFolia = AutoTreeChop.isFolia();
    }

    public void runTask(Runnable task) {
        UniversalScheduler.getScheduler(plugin).runTask(task);
    }

    public void runTaskAsync(Runnable task) {
        UniversalScheduler.getScheduler(plugin).runTaskAsynchronously(task);
    }

    public void runTaskLater(Runnable task, long delayTicks) {
        UniversalScheduler.getScheduler(plugin).runTaskLater(task, delayTicks);
    }

    /**
     * Schedule a delayed task (commonly used for leaf removal and replanting)
     */
    public void scheduleDelayed(Location location, Runnable task, long delayTicks) {
        runTaskLater(task, delayTicks);
    }
}