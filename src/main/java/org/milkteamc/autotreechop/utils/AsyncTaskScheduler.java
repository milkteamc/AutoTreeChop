package org.milkteamc.autotreechop.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;

public class AsyncTaskScheduler {

    private final AutoTreeChop plugin;
    private final boolean isFolia;

    public AsyncTaskScheduler(AutoTreeChop plugin) {
        this.plugin = plugin;
        this.isFolia = AutoTreeChop.isFolia();
    }

    /**
     * Run a task immediately on the main thread (or appropriate region for Folia)
     */
    public void runTask(Location location, Runnable task) {
        if (isFolia) {
            plugin.getServer().getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Run a task asynchronously (or immediately if async is disabled)
     */
    public void runTaskAsync(Config config, Runnable task) {
        if (config.isChopTreeAsync() && !isFolia) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        } else {
            task.run();
        }
    }

    /**
     * Run a task with delay on the main thread
     */
    public void runTaskLater(Location location, Runnable task, long delayTicks) {
        if (isFolia) {
            plugin.getServer().getRegionScheduler().runDelayed(
                    plugin,
                    location,
                    scheduledTask -> task.run(),
                    delayTicks
            );
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Schedule a discovery task (may be async depending on config)
     * Then schedule validation on main thread
     */
    public void scheduleDiscoveryAndValidation(
            Config config,
            Location location,
            Runnable discoveryTask,
            Runnable validationTask) {

        Runnable wrappedDiscovery = () -> {
            discoveryTask.run();
            // After discovery, run validation on main thread
            runTask(location, validationTask);
        };

        runTaskAsync(config, wrappedDiscovery);
    }

    /**
     * Schedule a delayed task (commonly used for leaf removal and replanting)
     */
    public void scheduleDelayed(Location location, Runnable task, long delayTicks) {
        runTaskLater(location, task, delayTicks);
    }

    /**
     * Check if running on Folia
     */
    public boolean isFolia() {
        return isFolia;
    }
}