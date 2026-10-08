package ink.ziip.championshipscore.platform.bukkit.bingo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

class BingoObjectiveProgressTrackerTest {
    @Test
    void keepsDistinctValuesAndCountersIsolatedByPlayerAndBucket() {
        BingoObjectiveProgressTracker tracker = new BingoObjectiveProgressTracker();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        tracker.recordDistinct(alice, "craft", "FURNACE");
        tracker.recordDistinct(alice, "craft", "FURNACE");
        tracker.recordDistinct(alice, "eat", "APPLE");
        tracker.recordDistinct(bob, "craft", "FURNACE");
        tracker.increment(alice, "kills");
        tracker.increment(alice, "kills");

        assertEquals(1, tracker.distinctCount(alice, "craft"));
        assertEquals(1, tracker.distinctCount(alice, "eat"));
        assertEquals(1, tracker.distinctCount(bob, "craft"));
        assertEquals(2, tracker.count(alice, "kills"));
        assertEquals(0, tracker.count(bob, "kills"));
    }

    @Test
    void elapsedObservationPausesWithoutCountingInactiveTime() {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        BingoObjectiveProgressTracker tracker = new BingoObjectiveProgressTracker(clock::get);
        UUID playerId = UUID.randomUUID();

        assertEquals(0L, tracker.observeElapsed(playerId, "pumpkin", true));
        clock.addAndGet(1_500_000_000L);
        assertEquals(1_500L, tracker.observeElapsed(playerId, "pumpkin", true));
        assertEquals(1_500L, tracker.observeElapsed(playerId, "pumpkin", false));
        clock.addAndGet(20_000_000_000L);
        assertEquals(1_500L, tracker.observeElapsed(playerId, "pumpkin", true));
        clock.addAndGet(500_000_000L);
        assertEquals(2_000L, tracker.observeElapsed(playerId, "pumpkin", true));

        tracker.resetAll();
        assertEquals(0L, tracker.observeElapsed(playerId, "pumpkin", false));
        assertEquals(0, tracker.count(playerId, "kills"));
    }
}
