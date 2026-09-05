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

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.milkteamc.autotreechop.database.DatabaseManager.PlayerData;

@Timeout(10)
class PlayerDataWriteQueueTest {
    private final UUID uuid = UUID.randomUUID();

    private PlayerData data(int uses) {
        return new PlayerData(uuid, true, uses, uses * 10, LocalDate.now());
    }

    @Test
    void failedOfflineSnapshotSurvivesAndEmptySaveRetriesIt() {
        AtomicBoolean failing = new AtomicBoolean(true);
        AtomicInteger stored = new AtomicInteger();
        try (var queue = new PlayerDataWriteQueue(batch -> {
            if (failing.get()) throw new IllegalStateException("database unavailable");
            stored.set(batch.iterator().next().getDailyUses());
        })) {
            assertThrows(CompletionException.class, () -> queue.save(List.of(data(3)))
                    .join());
            assertEquals(3, queue.read(() -> queue.getPending(uuid)).join().getDailyUses());
            failing.set(false);
            queue.save(List.of()).join();
            assertEquals(3, stored.get());
            assertNull(queue.getPending(uuid));
        }
    }

    @Test
    void newerSnapshotIsNotAcknowledgedByOlderInFlightWrite() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger stored = new AtomicInteger();
        try (var queue = new PlayerDataWriteQueue(batch -> {
            if (calls.incrementAndGet() == 1) {
                started.countDown();
                await(release);
            }
            stored.set(batch.iterator().next().getDailyUses());
        })) {
            var first = queue.save(List.of(data(1)));
            try {
                assertTrue(started.await(3, TimeUnit.SECONDS));
                PlayerData newer = data(2);
                var second = queue.save(List.of(newer));
                newer.setDailyUses(99); // Submitted snapshots must be defensive copies.
                release.countDown();
                CompletableFuture.allOf(first, second).join();
                assertEquals(2, stored.get());
                assertEquals(2, calls.get());
                assertNull(queue.getPending(uuid));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void failedOldWriteCannotReplaceNewerSnapshot() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger stored = new AtomicInteger();
        try (var queue = new PlayerDataWriteQueue(batch -> {
            if (calls.incrementAndGet() == 1) {
                started.countDown();
                await(release);
                throw new IllegalStateException("first write failed");
            }
            stored.set(batch.iterator().next().getDailyUses());
        })) {
            var first = queue.save(List.of(data(1)));
            try {
                assertTrue(started.await(3, TimeUnit.SECONDS));
                var second = queue.save(List.of(data(2)));
                release.countDown();
                assertThrows(CompletionException.class, first::join);
                second.join();
                assertEquals(2, stored.get());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void closeRetriesFailuresAndRejectsNewWork() {
        AtomicInteger calls = new AtomicInteger();
        var queue = new PlayerDataWriteQueue(batch -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("temporary failure");
        });
        assertThrows(
                CompletionException.class, () -> queue.save(List.of(data(1))).join());
        queue.close();
        assertEquals(2, calls.get());
        assertNull(queue.getPending(uuid));
        assertThrows(
                CompletionException.class, () -> queue.save(List.of(data(2))).join());
        assertThrows(CompletionException.class, () -> queue.read(() -> 1).join());
    }

    @Test
    void closeReportsPersistentFailureAndKeepsSnapshot() {
        var queue = new PlayerDataWriteQueue(batch -> {
            throw new IllegalStateException("offline");
        });
        assertThrows(
                CompletionException.class, () -> queue.save(List.of(data(4))).join());
        assertThrows(CompletionException.class, queue::close);
        assertEquals(4, queue.getPending(uuid).getDailyUses());
    }

    @Test
    void closeWaitsForInFlightWork() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var queue = new PlayerDataWriteQueue(batch -> {
            started.countDown();
            await(release);
        });
        queue.save(List.of(data(1)));
        try {
            assertTrue(started.await(3, TimeUnit.SECONDS));
            var closing = CompletableFuture.runAsync(queue::close);
            assertFalse(closing.isDone());
            release.countDown();
            closing.get(3, TimeUnit.SECONDS);
            assertNull(queue.getPending(uuid));
        } finally {
            release.countDown();
            queue.close();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for test latch");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
