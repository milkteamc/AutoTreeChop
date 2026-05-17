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
import org.bukkit.Location;
import org.milkteamc.autotreechop.AutoTreeChop;

public class BatchProcessor {

    private final AsyncTaskScheduler scheduler;

    public BatchProcessor(AutoTreeChop plugin, AsyncTaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Process a list of locations in batches.
     *
     * <p><b>Fast path:</b> when all locations fit within a single batch
     * (i.e. {@code locations.size() <= batchSize}) the entire work is dispatched
     * as one region task.  This avoids the recursive scheduling overhead that the
     * general path incurs even when only one batch is needed (a very common case
     * for small trees).
     *
     * <p>IMPORTANT: All processing happens on the REGION thread for Folia compatibility.
     *
     * @param locations  list of locations to process
     * @param startIndex starting index
     * @param batchSize  number of items to process per batch
     * @param processor  function to process each location: (location, index) -&gt; void
     * @param onComplete callback when all batches are complete
     */
    public void processBatch(
            List<Location> locations,
            int startIndex,
            int batchSize,
            BiConsumer<Location, Integer> processor,
            Runnable onComplete) {

        if (locations.isEmpty()) {
            if (onComplete != null) {
                scheduler.runTask(onComplete);
            }
            return;
        }

        // Fast path: everything fits in one batch – avoid recursive scheduling overhead
        if (startIndex == 0 && locations.size() <= batchSize) {
            Location first = locations.get(0);
            scheduler.runTaskAtLocation(first, () -> {
                for (int i = 0; i < locations.size(); i++) {
                    processor.accept(locations.get(i), i);
                }
                if (onComplete != null) {
                    onComplete.run();
                }
            });
            return;
        }

        processBatchInternal(locations, startIndex, batchSize, processor, onComplete, 1L);
    }

    /**
     * Process batches with a custom inter-batch delay.
     *
     * @param locations   list of locations to process
     * @param startIndex  starting index
     * @param batchSize   number of items to process per batch
     * @param processor   function to process each location: (location, index) -&gt; void
     * @param onComplete  callback when all batches are complete
     * @param delayTicks  delay between batches in ticks
     */
    public void processBatchWithDelay(
            List<Location> locations,
            int startIndex,
            int batchSize,
            BiConsumer<Location, Integer> processor,
            Runnable onComplete,
            long delayTicks) {

        if (locations.isEmpty()) {
            if (onComplete != null) {
                scheduler.runTask(onComplete);
            }
            return;
        }

        processBatchInternal(locations, startIndex, batchSize, processor, onComplete, delayTicks);
    }

    /**
     * Internal batch processing implementation.
     * Uses REGION scheduler to ensure Folia compatibility.
     */
    private void processBatchInternal(
            List<Location> locations,
            int startIndex,
            int batchSize,
            BiConsumer<Location, Integer> processor,
            Runnable onComplete,
            long delayTicks) {

        if (locations.isEmpty() || startIndex >= locations.size()) {
            if (onComplete != null) {
                if (!locations.isEmpty()) {
                    scheduler.runTaskAtLocation(locations.get(0), onComplete);
                } else {
                    scheduler.runTask(onComplete);
                }
            }
            return;
        }

        int endIndex = Math.min(startIndex + batchSize, locations.size());
        boolean isLastBatch = endIndex >= locations.size();

        Location batchLocation = locations.get(startIndex);

        Runnable batchTask = () -> {
            for (int i = startIndex; i < endIndex; i++) {
                Location location = locations.get(i);
                processor.accept(location, i);
            }

            if (isLastBatch) {
                if (onComplete != null) {
                    onComplete.run();
                }
            } else {
                Location nextLocation = locations.get(endIndex);
                Runnable nextBatch =
                        () -> processBatchInternal(locations, endIndex, batchSize, processor, onComplete, delayTicks);
                scheduler.runTaskLaterAtLocation(nextLocation, nextBatch, delayTicks);
            }
        };

        scheduler.runTaskAtLocation(batchLocation, batchTask);
    }

    /**
     * Process batches with early-termination support.
     *
     * <p>Same single-batch fast path as {@link #processBatch} is applied here.
     *
     * @param locations  list of locations to process
     * @param startIndex starting index
     * @param batchSize  number of items to process per batch
     * @param processor  function to process each location; return {@code false} to stop
     * @param onComplete callback when complete or stopped
     */
    public void processBatchWithTermination(
            List<Location> locations,
            int startIndex,
            int batchSize,
            java.util.function.BiFunction<Location, Integer, Boolean> processor,
            Runnable onComplete) {

        if (locations.isEmpty()) {
            if (onComplete != null) {
                scheduler.runTask(onComplete);
            }
            return;
        }

        // Fast path: single batch
        if (startIndex == 0 && locations.size() <= batchSize) {
            Location first = locations.get(0);
            scheduler.runTaskAtLocation(first, () -> {
                for (int i = 0; i < locations.size(); i++) {
                    if (!processor.apply(locations.get(i), i)) break;
                }
                if (onComplete != null) {
                    onComplete.run();
                }
            });
            return;
        }

        if (startIndex >= locations.size()) {
            if (onComplete != null) {
                scheduler.runTaskAtLocation(locations.get(0), onComplete);
            }
            return;
        }

        Location batchLocation = locations.get(startIndex);

        Runnable batchTask = () -> {
            int endIndex = Math.min(startIndex + batchSize, locations.size());

            boolean shouldContinue = true;
            int i = startIndex;
            for (; i < endIndex && shouldContinue; i++) {
                shouldContinue = processor.apply(locations.get(i), i);
            }

            boolean isLastBatch = i >= locations.size();

            if (!shouldContinue || isLastBatch) {
                if (onComplete != null) {
                    onComplete.run();
                }
            } else {
                Location nextLocation = locations.get(i);
                int finalI = i;
                Runnable nextBatch =
                        () -> processBatchWithTermination(locations, finalI, batchSize, processor, onComplete);
                scheduler.runTaskLaterAtLocation(nextLocation, nextBatch, 1L);
            }
        };

        scheduler.runTaskAtLocation(batchLocation, batchTask);
    }
}
