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
 
package org.milkteamc.autotreechop.database;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.milkteamc.autotreechop.database.DatabaseManager.PlayerData;

/** Serializes database work and retains the latest unsaved snapshot, including offline players. */
final class PlayerDataWriteQueue implements AutoCloseable {
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "AutoTreeChop-database"));
    private final Map<UUID, PlayerData> pending = new HashMap<>();
    private final Consumer<Collection<PlayerData>> writer;
    private CompletableFuture<Void> closing;

    PlayerDataWriteQueue(Consumer<Collection<PlayerData>> writer) {
        this.writer = writer;
    }

    synchronized CompletableFuture<Void> save(Collection<PlayerData> snapshots) {
        if (closing != null) return CompletableFuture.failedFuture(new IllegalStateException("Database is closing"));
        for (PlayerData data : snapshots) {
            pending.put(data.getPlayerUUID(), new PlayerData(data));
        }
        // Empty submissions also retry data retained after an earlier failure.
        return CompletableFuture.runAsync(this::flush, executor);
    }

    synchronized <T> CompletableFuture<T> read(Supplier<T> reader) {
        if (closing != null) return CompletableFuture.failedFuture(new IllegalStateException("Database is closing"));
        return CompletableFuture.supplyAsync(reader, executor);
    }

    synchronized PlayerData getPending(UUID uuid) {
        PlayerData data = pending.get(uuid);
        return data == null ? null : new PlayerData(data);
    }

    private void flush() {
        Map<UUID, PlayerData> batch;
        synchronized (this) {
            batch = new HashMap<>(pending);
        }
        if (batch.isEmpty()) return;
        writer.accept(batch.values());
        synchronized (this) {
            // A newer snapshot may have arrived while this batch was being written.
            batch.forEach((uuid, data) -> pending.remove(uuid, data));
        }
    }

    @Override
    public void close() {
        CompletableFuture<Void> completion;
        synchronized (this) {
            if (closing == null) {
                closing = CompletableFuture.runAsync(this::flush, executor);
                executor.shutdown();
            }
            completion = closing;
        }
        completion.join();
    }
}
