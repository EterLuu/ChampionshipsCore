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

    @Test
    void randomizedSnapshotsAgreeWithTheOriginalRankingFormula() {
        Random random = new Random(7349);
        for (int race = 0; race < 100; race++) {
            var progress = new AceRaceProgress();
            var roster = new ArrayList<UUID>();
            var finished = new ArrayList<UUID>();
            Map<UUID, Integer> values = new HashMap<>();
            for (int i = 0; i < 40; i++) {
                UUID player = new UUID(race, i);
                roster.add(player);
                int laps = random.nextInt(4), point = random.nextInt(9);
                for (int lap = 0; lap < laps; lap++) progress.completeLap(player);
                progress.reachPoint(player, point);
                values.put(player, laps * 8 + point);
                if (random.nextInt(5) == 0) {
                    progress.finish(player);
                    finished.add(player);
                }
            }
            for (UUID player : roster) {
                int place = finished.indexOf(player);
                int first, last;
                if (place >= 0) first = last = place + 1;
                else {
                    int ahead = finished.size(), tied = 0;
                    for (UUID other : roster) {
                        if (finished.contains(other)) continue;
                        if (values.get(other) > values.get(player)) ahead++;
                        else if (values.get(other).equals(values.get(player))) tied++;
                    }
                    first = ahead + 1;
                    last = first + Math.max(1, tied) - 1;
                }
                assertEquals(
                        new AceRaceProgress.PlacementRange(first, last),
                        progress.placement(player, roster, 8));
            }
        }
    }
}
