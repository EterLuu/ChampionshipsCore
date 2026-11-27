package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;

import org.junit.jupiter.api.Test;

import java.util.*;

class SliderModelTest {
    @Test
    void sharedDeckIsReproducibleAndEveryRetryChangesTheTarget() {
        var shapes = new HashSet<SliderShape>();
        for (int seed = -40; seed < 160; seed++)
            for (var station : SliderHall.values())
                for (int round = 1; round <= SliderStationState.ROUNDS; round++) {
                    int last = -1;
                    for (int attempt = 0; attempt < 21; attempt++) {
                        var puzzle = new SliderDeck(seed).puzzle(station, round, attempt);
                        assertEquals(puzzle, new SliderDeck(seed).puzzle(station, round, attempt));
                        if (round == 3 && attempt == 0) {
                            var deck = new SliderDeck(seed);
                            assertNotEquals(
                                    deck.puzzle(station, 1, 0).target().column(),
                                    puzzle.target().column());
                            assertNotEquals(
                                    deck.puzzle(station, 2, 0).target().column(),
                                    puzzle.target().column());
                        }
                        assertNotEquals(last, puzzle.target().column());
                        last = puzzle.target().column();
                        assertTrue(puzzle.row() >= 1 && puzzle.row() <= 7);
                        assertEquals(round == 1 ? 1 : 3, puzzle.notches().size());
                        var seen = new HashSet<SliderShape>();
                        for (var hole : puzzle.notches()) {
                            assertTrue(seen.add(hole.shape()));
                            shapes.add(hole.shape());
                            assertTrue(hole.column() >= 4 && hole.column() <= 22);
                            for (var other : puzzle.notches())
                                if (other != hole)
                                    assertTrue(Math.abs(hole.column() - other.column()) >= 5);
                        }
                    }
                }
        assertEquals(48, shapes.size());
        assertEquals(Set.copyOf(SliderShape.STYLES), shapes);
    }

    @Test
    void oppositeRailsWaitThroughRetriesAndDepartureAndAdvanceTogetherAllThreeTimes() {
        var first = new SliderStationState();
        var second = new SliderStationState();
        var a = UUID.randomUUID();
        var c = UUID.randomUUID();
        for (int round = 1; round <= 3; round++) {
            int start = round * 200;
            if (round == 1) assertTrue(second.grip(c, start));
            assertTrue(first.grip(a, start));
            assertEquals(SliderStationState.Result.ALIGNED, first.submit(a, 7, 7, start + 5));
            assertFalse(first.confirm(second, start + 5));
            assertEquals(round == 2 ? 2 : 1, first.pictureRound());
            assertEquals(SliderStationState.Result.LOCKED, first.submit(a, 7, 7, start + 50));
            first.leave(a);
            assertFalse(first.grip(a, start + 60));
            assertFalse(first.advance(start + 65, false));
            assertEquals(SliderStationState.Result.FAIL, second.submit(c, 11, 12, start + 70));
            assertFalse(first.confirm(second, start + 70));
            assertEquals(SliderStationState.Result.ALIGNED, second.submit(c, 12, 12, start + 110));
            assertTrue(first.confirm(second, start + 110));
            assertTrue(second.confirm(first, start + 110));
            assertFalse(first.confirm(second, start + 111));
            assertEquals(5, first.solveDuration());
            assertFalse(first.advance(start + 139, false));
            assertFalse(second.advance(start + 139, false));
            assertTrue(first.advance(start + 140, false));
            assertTrue(second.advance(start + 140, false));
            assertEquals(first.round(), second.round());
            assertEquals(round == 3, first.complete());
            assertEquals(first.complete(), second.complete());
        }
    }

    @Test
    void partnerArrivingDuringSoloPassFeedbackSharesTheTransition() {
        var a = new SliderStationState();
        var c = new SliderStationState();
        var first = UUID.randomUUID();
        var late = UUID.randomUUID();
        a.grip(first, 0);
        a.submit(first, 4, 4, 5);
        assertTrue(a.confirm(null, 5));
        c.grip(late, 8);
        c.submit(late, 8, 8, 10);
        assertTrue(c.confirm(a, 10));
        assertFalse(a.advance(35, false));
        assertTrue(a.advance(40, false));
        assertTrue(c.advance(40, false));
    }

    @Test
    void latePartnerCatchesUpBeforeTheNextJointPass() {
        var a = new SliderStationState();
        var c = new SliderStationState();
        var first = UUID.randomUUID();
        var late = UUID.randomUUID();
        a.grip(first, 0);
        a.submit(first, 4, 4, 5);
        assertTrue(a.confirm(null, 5));
        assertTrue(a.advance(35, false));
        a.submit(first, 7, 7, 40);
        c.grip(late, 41);
        c.submit(late, 6, 6, 43);
        assertFalse(a.confirm(c, 43));
        assertTrue(c.confirm(a, 43));
        assertTrue(c.advance(73, false));
        assertFalse(a.confirm(c, 73));
        c.submit(late, 8, 8, 80);
        assertTrue(a.confirm(c, 80));
        assertTrue(c.confirm(a, 80));
        assertTrue(a.advance(110, false));
        assertTrue(c.advance(110, false));
    }

    @Test
    void holderWhoMetTheQuotaIsReleasedAndTheNextRoundStartsAtTheNextGrip() {
        var s = new SliderStationState();
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        assertTrue(s.grip(a, 10));
        assertEquals(SliderStationState.Result.ALIGNED, s.submit(a, 6, 6, 20));
        assertTrue(s.confirm(null, 20));
        assertEquals(a, s.holder());
        assertFalse(s.advance(49, true));
        assertTrue(s.advance(50, true));
        assertEquals(2, s.round());
        assertNull(s.holder());
        assertFalse(s.complete());
        assertTrue(s.grip(b, 70));
        assertEquals(SliderStationState.Result.ALIGNED, s.submit(b, 9, 9, 80));
        assertTrue(s.confirm(null, 80));
        assertTrue(s.advance(110, true));
        assertFalse(s.complete());
        assertEquals(3, s.round());
        assertNull(s.holder());
        assertTrue(s.grip(b, 112));
        assertEquals(SliderStationState.Result.ALIGNED, s.submit(b, 14, 14, 125));
        assertTrue(s.confirm(null, 125));
        assertTrue(s.advance(155, true));
        assertTrue(s.complete());
        assertNull(s.holder());
    }

    @Test
    void locksDepartureTimingAndThreeRoundCompletion() {
        var s = new SliderStationState();
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        assertEquals(1, s.pictureRound());
        assertTrue(s.grip(a, 10));
        assertFalse(s.grip(b, 11));
        assertTrue(s.intruder(b));
        assertTrue(s.move(a, 4, 12));
        assertEquals(4, s.column());
        assertEquals(SliderStationState.Result.IGNORE, s.submit(b, 4, 4, 13));
        assertEquals(SliderStationState.Result.FAIL, s.submit(a, 4, 8, 14));
        assertEquals(1, s.attempt());
        assertEquals(0, s.column());
        assertFalse(s.move(a, 1, 15));
        assertEquals(SliderStationState.Result.LOCKED, s.submit(a, 8, 8, 53));
        s.leave(a);
        assertFalse(s.grip(b, 53));
        assertTrue(s.grip(b, 54));
        assertEquals(1, s.attempt());
        assertEquals(SliderStationState.Result.ALIGNED, s.submit(b, 8, 8, 55));
        assertEquals(45, s.solveDuration());
        assertTrue(s.confirm(null, 55));
        assertEquals(2, s.pictureRound());
        assertFalse(s.advance(84, false));
        assertTrue(s.advance(85, false));
        assertEquals(2, s.round());
        assertEquals(b, s.holder());
        assertEquals(0, s.attempt());
        s.leave(b);
        assertTrue(s.grip(a, 88));
        assertEquals(SliderStationState.Result.ALIGNED, s.submit(a, 20, 20, 89));
        assertTrue(s.confirm(null, 89));
        assertEquals(1, s.pictureRound());
        s.leave(a);
        assertFalse(s.grip(b, 110));
        assertTrue(s.advance(119, false));
        assertFalse(s.complete());
        assertTrue(s.grip(a, 124));
        assertEquals(1, s.pictureRound());
        assertEquals(SliderStationState.Result.ALIGNED, s.submit(a, 10, 10, 130));
        assertTrue(s.confirm(null, 130));
        assertTrue(s.advance(160, false));
        assertEquals(1, s.pictureRound());
        assertTrue(s.complete());
        assertNull(s.holder());
        assertFalse(s.grip(b, 200));
        assertFalse(s.advance(200, false));
    }

    @Test
    void emptyStationStartsNewRoundClockOnlyWhenGripped() {
        var s = new SliderStationState();
        var a = UUID.randomUUID();
        s.grip(a, 10);
        s.submit(a, 4, 4, 15);
        s.leave(a);
        assertTrue(s.confirm(null, 15));
        assertTrue(s.advance(45, false));
        assertTrue(s.grip(a, 66));
        s.submit(a, 9, 9, 68);
        assertEquals(2, s.solveDuration());
    }
}
