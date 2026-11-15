package ink.ziip.championshipscore.api.game.acerace.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class AceRaceProgressTest {
    @Test
    void tiedProgressFinishOrderAndLapCrossingsPreservePlacementRanges() {
        var progress = new AceRaceProgress();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        List<UUID> roster = List.of(a, b, c);
        assertEquals(new AceRaceProgress.PlacementRange(1, 3), progress.placement(a, roster, 4));
        progress.reachPoint(b, 4);
        progress.completeLap(a);
        assertEquals(new AceRaceProgress.PlacementRange(1, 2), progress.placement(a, roster, 4));
        assertEquals(new AceRaceProgress.PlacementRange(1, 2), progress.placement(b, roster, 4));
        assertEquals(1, progress.finish(c));
        assertEquals(0, progress.finish(c));
        assertEquals(new AceRaceProgress.PlacementRange(1, 1), progress.placement(c, roster, 4));
        assertEquals(new AceRaceProgress.PlacementRange(2, 3), progress.placement(a, roster, 4));
        assertEquals(List.of(c), progress.finishedPlayers());
    }

    @Test
    void cacheTracksAllInputsAndReturnedSnapshotsCannotMutateTheRace() {
        var progress = new AceRaceProgress();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var roster = new ArrayList<>(List.of(a, b));
        var first = progress.placements(roster, 4);
        assertSame(first, progress.placements(List.copyOf(roster), 4));
        assertThrows(UnsupportedOperationException.class, () -> first.clear());
        progress.reachPoint(a, 1);
        var advanced = progress.placements(roster, 4);
        assertNotSame(first, advanced);
        assertSame(advanced, progress.placements(roster, 4));
        progress.completeLap(b);
        assertEquals(1, progress.placement(b, roster, 4).first());
        assertEquals(2, progress.placement(b, roster, 0).first());
        progress.finish(a);
        roster.remove(a);
        assertEquals(new AceRaceProgress.PlacementRange(0, 0), progress.placement(a, roster, 4));
        assertEquals(2, progress.placement(b, roster, 4).first());
        progress.reset();
        assertEquals(1, progress.placement(b, roster, 4).first());
        assertTrue(progress.finishedPlayers().isEmpty());
        assertEquals(0, progress.completedLaps(b));
        assertEquals(0, progress.nextPoint(a));
    }
}
