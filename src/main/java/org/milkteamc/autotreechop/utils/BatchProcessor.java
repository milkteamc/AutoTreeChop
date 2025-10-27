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
     *
     * @param locations List of locations to process
     * @param startIndex Starting index
     * @param batchSize Number of items to process per batch
     * @param processor Function to process each location (location, index) -> void
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
     * @param locations List of locations to process
     * @param startIndex Starting index
     * @param batchSize Number of items to process per batch
     * @param processor Function to process each location (location, index) -> void
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
                onComplete.run();
            }
            return;
        }

        int endIndex = Math.min(startIndex + batchSize, locations.size());
        boolean isLastBatch = endIndex >= locations.size();

        // Process current batch
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
            Location nextLocation = locations.get(endIndex);
            Runnable nextBatch = () -> processBatchInternal(
                    locations, endIndex, batchSize, processor, onComplete, delayTicks
            );
            scheduler.runTaskLater(nextLocation, nextBatch, delayTicks);
        }
    }

    /**
     * @param locations List of locations to process
     * @param startIndex Starting index
     * @param batchSize Number of items to process per batch
     * @param processor Function to process each location, returns false to stop
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
                onComplete.run();
            }
            return;
        }

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
            scheduler.runTaskLater(nextLocation, nextBatch, 1L);
        }
    }

    /**
     * Get optimal batch size based on total items
     * For very large operations, use smaller batches
     */
    public static int getOptimalBatchSize(int totalItems, int configuredBatchSize) {
        if (totalItems > 1000) {
            return Math.min(configuredBatchSize, 30);
        } else if (totalItems > 500) {
            return Math.min(configuredBatchSize, 40);
        }
        return configuredBatchSize;
    }
}