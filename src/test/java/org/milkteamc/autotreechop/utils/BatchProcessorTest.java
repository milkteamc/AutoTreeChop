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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class BatchProcessorTest {
    private final AsyncTaskScheduler scheduler = mock(AsyncTaskScheduler.class);
    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private final BatchProcessor processor = new BatchProcessor(null, scheduler);
    private final List<Location> locations =
            List.of(new Location(null, 0, 1, 0), new Location(null, 1, 1, 0), new Location(null, 32, 1, 0));

    private void captureTasks() {
        doAnswer(call -> {
                    tasks.add(call.getArgument(1));
                    return null;
                })
                .when(scheduler)
                .runTaskAtLocation(any(), any());
        doAnswer(call -> {
                    tasks.add(call.getArgument(1));
                    return null;
                })
                .when(scheduler)
                .runTaskLaterAtLocation(any(), any(), anyLong());
    }

    @Test
    void eachLocationRunsOnceAndCompletionRunsOnce() {
        captureTasks();
        List<Integer> seen = new ArrayList<>();
        Runnable complete = mock(Runnable.class);
        processor.processBatch(locations, 0, 2, (location, index) -> seen.add(index), complete);
        while (!tasks.isEmpty()) tasks.remove().run();
        assertEquals(List.of(0, 1, 2), seen);
        verify(complete).run();
    }

    @Test
    void exceptionCleansUpOnceAndStopsRemainingWork() {
        captureTasks();
        Runnable complete = mock(Runnable.class);
        processor.processBatch(
                locations,
                0,
                1,
                (location, index) -> {
                    throw new IllegalStateException("hook failure");
                },
                complete);
        assertThrows(IllegalStateException.class, () -> tasks.remove().run());
        verify(complete).run();
        assertTrue(tasks.isEmpty());
    }

    @Test
    void earlyTerminationDoesNotScheduleMoreWork() {
        captureTasks();
        Runnable complete = mock(Runnable.class);
        processor.processBatchWithTermination(locations, 0, 1, (location, index) -> false, complete);
        tasks.remove().run();
        verify(complete).run();
        assertTrue(tasks.isEmpty());
    }

    @Test
    void regionBoundaryIsRescheduledBeforeAccess() {
        captureTasks();
        try (var access = mockStatic(RegionAccess.class)) {
            access.when(() -> RegionAccess.owns(locations.get(0))).thenReturn(true);
            List<Integer> seen = new ArrayList<>();
            Runnable complete = mock(Runnable.class);
            processor.processBatch(locations, 0, 3, (location, index) -> seen.add(index), complete);
            tasks.remove().run();
            assertEquals(List.of(0), seen);
            verify(scheduler).runTaskLaterAtLocation(eq(locations.get(1)), any(), eq(1L));
            access.when(() -> RegionAccess.owns(locations.get(1))).thenReturn(true);
            access.when(() -> RegionAccess.owns(locations.get(2))).thenReturn(true);
            tasks.remove().run();
            assertEquals(List.of(0, 1, 2), seen);
            verify(complete).run();
        }
    }
}
