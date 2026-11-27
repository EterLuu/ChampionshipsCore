package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.*;

class SliderWorkTest {
    @Test
    void competingLanesShareOneBudgetAndStartTheNextTickWhereTheyStopped() {
        var trace = new ArrayList<Integer>();
        var jobs = new ArrayDeque<SliderWork.Job>();
        for (int i = 0; i < 3; i++) {
            int lane = i;
            jobs.add(
                    new SliderWork.Job(
                            () -> true,
                            () -> {
                                trace.add(lane);
                                return false;
                            },
                            2,
                            false));
        }
        SliderWork.slice(jobs, () -> 0, 4, 100);
        assertEquals(List.of(0, 0, 1, 1), trace);
        SliderWork.slice(jobs, () -> 0, 3, 100);
        assertEquals(List.of(0, 0, 1, 1, 2, 2, 0), trace);
    }

    @Test
    void slowWorkYieldsAfterOneOperationAndStaleSlicesCannotMutateAReplacement() {
        var generation = new AtomicInteger(1);
        var writes = new AtomicInteger();
        var time = new AtomicLong();
        var jobs = new ArrayDeque<SliderWork.Job>();
        jobs.add(
                new SliderWork.Job(
                        () -> generation.get() == 1,
                        () -> {
                            writes.incrementAndGet();
                            time.addAndGet(12);
                            return false;
                        },
                        8,
                        false));
        SliderWork.slice(jobs, time::get, 64, 5);
        assertEquals(1, writes.get());
        generation.incrementAndGet();
        jobs.add(new SliderWork.Job(() -> generation.get() == 2, () -> true, 1, false));
        SliderWork.slice(jobs, time::get, 64, 5);
        assertTrue(jobs.isEmpty());
        assertEquals(1, writes.get());
    }

    @Test
    void entranceFinishesOnlyItsOwnPendingRemovalAndCannotRunItTwice() throws Exception {
        var work = new SliderWork(null);
        // Seed the native scheduler queue without requiring a running Bukkit server.
        var field = SliderWork.class.getDeclaredField("jobs");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var queue = (ArrayDeque<SliderWork.Job>) field.get(work);
        var remaining = new AtomicInteger(3);
        var lane = new SliderWork.Job(() -> true, () -> remaining.decrementAndGet() == 0, 1, true);
        var other = new SliderWork.Job(() -> true, () -> fail("another lane was drained"), 1, true);
        queue.add(lane);
        queue.add(other);
        SliderWork.slice(queue, () -> 0, 1, 100);
        assertEquals(2, remaining.get());
        work.finishRemoval(lane);
        work.finishRemoval(lane);
        assertEquals(0, remaining.get());
        assertEquals(List.of(other), List.copyOf(queue));
    }

    @Test
    void sweepCompletesEachViewerLeftColumnBeforeStartingTheNext() {
        Set<Integer> seen = new HashSet<>();
        int cursor = 0;
        for (int column = 0; column < SliderHall.MAP_COLUMNS; column++) {
            for (int row = 0; row < SliderHall.MAP_ROWS; row++) {
                int slot = SliderScreen.columnFirst(cursor++);
                assertEquals(row * SliderHall.MAP_COLUMNS + column, slot);
                assertTrue(seen.add(slot));
            }
        }
        assertEquals(128, seen.size());
    }
}
