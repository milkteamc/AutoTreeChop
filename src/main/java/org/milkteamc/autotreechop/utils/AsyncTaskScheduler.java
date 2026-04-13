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

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import org.bukkit.Location;
import org.milkteamc.autotreechop.AutoTreeChop;

public class AsyncTaskScheduler {

    private final TaskScheduler scheduler;

    public AsyncTaskScheduler(AutoTreeChop plugin) {
        this.scheduler = UniversalScheduler.getScheduler(plugin);
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
