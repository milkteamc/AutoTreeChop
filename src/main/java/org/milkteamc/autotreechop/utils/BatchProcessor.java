package org.milkteamc.autotreechop.utils;

import org.bukkit.Location;
import org.milkteamc.autotreechop.AutoTreeChop;

import java.util.List;
import java.util.function.BiConsumer;

public class BatchProcessor {

    private final AutoTreeChop plugin;
    private final AsyncTaskScheduler scheduler;

    public BatchProcessor(AutoTreeChop plugin, AsyncTaskScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    /**
     * Process a list of locations in batches
     * IMPORTANT: All processing happens on REGION thread for Folia compatibility
     *
     * @param locations  List of locations to process
     * @param startIndex Starting index
     * @param batchSize  Number of items to process per batch
     * @param processor  Function to process each location (location, index) -> void
     * @param onComplete Callback when all batches are complete
     */
    public void processBatch(
            List<Location> locations,
            int startIndex,
            int batchSize,
            BiConsumer<Location, Integer> processor,
            Runnable onComplete) {

        processBatchInternal(locations, startIndex, batchSize, processor, onComplete, 1L);
    }

    /**
     * Process batches with custom delay
     *
     * @param locations  List of locations to process
     * @param startIndex Starting index
     * @param batchSize  Number of items to process per batch
     * @param processor  Function to process each location (location, index) -> void
     * @param onComplete Callback when all batches are complete
     * @param delayTicks Delay between batches in ticks
     */
    public void processBatchWithDelay(
            List<Location> locations,
            int startIndex,
            int batchSize,
            BiConsumer<Location, Integer> processor,
            Runnable onComplete,
            long delayTicks) {

        processBatchInternal(locations, startIndex, batchSize, processor, onComplete, delayTicks);
    }

    /**
     * Internal batch processing implementation
     * Uses REGION scheduler to ensure Folia compatibility
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
                // Run completion callback at first location's region
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

        // Get the first location in this batch for region scheduling
        Location batchLocation = locations.get(startIndex);

        // Process current batch on REGION thread
        Runnable batchTask = () -> {
            for (int i = startIndex; i < endIndex; i++) {
                Location location = locations.get(i);
                processor.accept(location, i);
            }

            // Schedule next batch or complete
            if (isLastBatch) {
                if (onComplete != null) {
                    onComplete.run();
                }
            } else {
                // Schedule next batch at the next batch's first location
                Location nextLocation = locations.get(endIndex);
                Runnable nextBatch = () -> processBatchInternal(
                        locations, endIndex, batchSize, processor, onComplete, delayTicks
                );
                scheduler.runTaskLaterAtLocation(nextLocation, nextBatch, delayTicks);
            }
        };

        // Execute this batch at the batch location's region
        scheduler.runTaskAtLocation(batchLocation, batchTask);
    }

    /**
     * Process batches with early termination support
     * Uses REGION scheduler to ensure Folia compatibility
     *
     * @param locations  List of locations to process
     * @param startIndex Starting index
     * @param batchSize  Number of items to process per batch
     * @param processor  Function to process each location, returns false to stop
     * @param onComplete Callback when complete or stopped
     */
    public void processBatchWithTermination(
            List<Location> locations,
            int startIndex,
            int batchSize,
            java.util.function.BiFunction<Location, Integer, Boolean> processor,
            Runnable onComplete) {

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

        // Get the first location in this batch for region scheduling
        Location batchLocation = locations.get(startIndex);

        // Process current batch on REGION thread
        Runnable batchTask = () -> {
            int endIndex = Math.min(startIndex + batchSize, locations.size());

            // Process current batch with termination check
            boolean shouldContinue = true;
            int i = startIndex;
            for (; i < endIndex && shouldContinue; i++) {
                Location location = locations.get(i);
                shouldContinue = processor.apply(location, i);
            }

            boolean isLastBatch = i >= locations.size();

            // Schedule next batch, complete, or terminate early
            if (!shouldContinue || isLastBatch) {
                if (onComplete != null) {
                    onComplete.run();
                }
            } else {
                Location nextLocation = locations.get(i);
                int finalI = i;
                Runnable nextBatch = () -> processBatchWithTermination(
                        locations, finalI, batchSize, processor, onComplete
                );
                scheduler.runTaskLaterAtLocation(nextLocation, nextBatch, 1L);
            }
        };

        // Execute this batch at the batch location's region
        scheduler.runTaskAtLocation(batchLocation, batchTask);
    }
}