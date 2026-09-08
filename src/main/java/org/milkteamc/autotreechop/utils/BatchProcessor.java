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

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import org.bukkit.Location;
import org.milkteamc.autotreechop.AutoTreeChop;

/** Sequential batches; re-dispatch at region boundaries and always clean up failed work. */
public class BatchProcessor {
    private final AsyncTaskScheduler scheduler;

    public BatchProcessor(AutoTreeChop plugin, AsyncTaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void processBatch(
            List<Location> locations,
            int startIndex,
            int batchSize,
            BiConsumer<Location, Integer> processor,
            Runnable onComplete) {
        processBatchWithDelay(locations, startIndex, batchSize, processor, onComplete, 1L);
    }

    public void processBatchWithDelay(
            List<Location> locations,
            int startIndex,
            int batchSize,
            BiConsumer<Location, Integer> processor,
            Runnable onComplete,
            long delayTicks) {
        process(
                locations,
                startIndex,
                batchSize,
                (location, index) -> {
                    processor.accept(location, index);
                    return true;
                },
                onComplete,
                delayTicks);
    }

    public void processBatchWithTermination(
            List<Location> locations,
            int startIndex,
            int batchSize,
            BiFunction<Location, Integer, Boolean> processor,
            Runnable onComplete) {
        process(locations, startIndex, batchSize, processor, onComplete, 1L);
    }

    private void process(
            List<Location> locations,
            int startIndex,
            int batchSize,
            BiFunction<Location, Integer, Boolean> processor,
            Runnable onComplete,
            long delayTicks) {
        if (batchSize <= 0 || startIndex < 0 || delayTicks < 0) {
            throw new IllegalArgumentException("Invalid batch size, start index or delay");
        }
        if (startIndex >= locations.size()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        scheduler.runTaskAtLocation(
                locations.get(startIndex),
                () -> runBatch(locations, startIndex, batchSize, processor, onComplete, delayTicks));
    }

    private void runBatch(
            List<Location> locations,
            int startIndex,
            int batchSize,
            BiFunction<Location, Integer, Boolean> processor,
            Runnable onComplete,
            long delayTicks) {
        int next = startIndex;
        boolean stopped = false;
        try {
            int end = (int) Math.min((long) startIndex + batchSize, locations.size());
            while (next < end) {
                Location location = locations.get(next);
                if (!RegionAccess.owns(location)) break;
                if (!processor.apply(location, next++)) {
                    stopped = true;
                    break;
                }
            }
        } catch (RuntimeException | Error failure) {
            if (onComplete != null) {
                try {
                    onComplete.run();
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
        if (stopped || next == locations.size()) {
            if (onComplete != null) onComplete.run();
        } else {
            int nextIndex = next;
            scheduler.runTaskLaterAtLocation(
                    locations.get(next),
                    () -> runBatch(locations, nextIndex, batchSize, processor, onComplete, delayTicks),
                    Math.max(1L, delayTicks));
        }
    }
}
