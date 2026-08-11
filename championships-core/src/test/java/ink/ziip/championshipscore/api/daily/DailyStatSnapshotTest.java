package ink.ziip.championshipscore.api.daily;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyStatSnapshotTest {
    @Test
    void accumulatesBingoProgressAndKeepsTheLargestSingleMatchCount() {
        DailyStatSnapshot snapshot = DailyStatSnapshot.EMPTY
                .add(true, 3, 25)
                .add(false, 1, 14);

        assertEquals(2, snapshot.gamesPlayed());
        assertEquals(1, snapshot.wins());
        assertEquals(4, snapshot.lineCount());
        assertEquals(39, snapshot.completedTasks());
        assertEquals(25, snapshot.maxCompletedTasks());
    }
}
