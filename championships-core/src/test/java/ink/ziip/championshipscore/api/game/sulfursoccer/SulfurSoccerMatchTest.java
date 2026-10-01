package ink.ziip.championshipscore.api.game.sulfursoccer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ink.ziip.championshipscore.api.game.sulfursoccer.SulfurSoccerSide.*;

class SulfurSoccerMatchTest {
    @Test void regulationStartsOnlyAtOfficialKickoffAndExpiresAtExactlyTwelveMinutes() {
        var match = new SulfurSoccerMatch(5);
        for (int tick = 0; tick < 200; tick++) assertFalse(match.tickRegulation());
        assertEquals(720, match.regulationSeconds());
        match.startWarmup(1);
        for (int tick = 0; tick < 20; tick++) {
            assertFalse(match.tickRegulation());
            match.tickWarmup();
        }
        assertEquals(720, match.regulationSeconds());
        match.kickOff();
        for (int tick = 0; tick < 720 * 20 - 1; tick++) assertFalse(match.tickRegulation());
        assertEquals(1, match.regulationSeconds());
        assertTrue(match.tickRegulation());
        assertEquals(0, match.regulationSeconds());
        assertTrue(match.shootout());
        assertNull(match.winner());
        match.kickOff();
        assertFalse(match.playing());
        assertFalse(match.enterGoal(LEFT));
        assertFalse(match.tickRegulation());
    }

    @Test void clockIncludesRestartCountdownsAndLeadingSideWinsAtTheLimit() {
        for (SulfurSoccerSide defender : SulfurSoccerSide.values()) {
            var match = new SulfurSoccerMatch(5);
            match.kickOff();
            match.enterGoal(defender);
            assertFalse(match.playing());
            for (int tick = 0; tick < 720 * 20 - 1; tick++) assertFalse(match.tickRegulation());
            assertTrue(match.tickRegulation());
            assertEquals(defender.opposite(), match.winner());
            assertFalse(match.shootout());
        }
    }

    @Test void threeMinuteWarmupNeverScoresAndEndsBeforeTheOfficialKickoff() {
        var match = new SulfurSoccerMatch(1);
        match.startWarmup(SulfurSoccerArea.WARMUP_SECONDS);
        assertEquals(180, match.warmupSeconds());
        for (int tick = 0; tick < 3599; tick++) {
            assertTrue(match.playing());
            assertFalse(match.enterGoal(tick % 2 == 0 ? LEFT : RIGHT));
            assertFalse(match.tickWarmup());
        }
        assertEquals(1, match.warmupSeconds());
        assertTrue(match.tickWarmup());
        assertFalse(match.warmingUp());
        assertFalse(match.playing());
        assertEquals(0, match.goals(LEFT));
        assertEquals(0, match.goals(RIGHT));
        assertNull(match.winner());
        assertFalse(match.enterGoal(LEFT));
        match.kickOff();
        assertTrue(match.enterGoal(LEFT));
        assertEquals(RIGHT, match.winner());
    }

    @Test void ballResetCanStopPlayWithoutScoringOrRestartingWarmup() {
        var match = new SulfurSoccerMatch(5);
        match.kickOff();
        match.stopPlay();
        assertFalse(match.enterGoal(LEFT));
        match.kickOff();
        assertTrue(match.enterGoal(LEFT));
        assertThrows(IllegalStateException.class, () -> match.startWarmup(180));
    }

    @Test void goalIsAwardedToOpponentAndOnlyOncePerKickoff() {
        var match = new SulfurSoccerMatch(5);
        assertFalse(match.enterGoal(RIGHT));
        match.kickOff();
        assertTrue(match.enterGoal(RIGHT));
        assertEquals(1, match.goals(LEFT));
        assertEquals(0, match.goals(RIGHT));
        assertFalse(match.enterGoal(RIGHT));
        assertFalse(match.enterGoal(LEFT));
        match.kickOff();
        assertTrue(match.enterGoal(LEFT));
        assertEquals(1, match.goals(RIGHT));
        assertNull(match.winner());
    }

    @Test void reachesConfiguredWinningScoreAndNeverRestartsAfterWinning() {
        var match = new SulfurSoccerMatch(2);
        match.kickOff();
        match.enterGoal(LEFT);
        match.kickOff();
        match.enterGoal(RIGHT);
        match.kickOff();
        match.enterGoal(LEFT);
        assertEquals(RIGHT, match.winner());
        assertEquals(2, match.goals(RIGHT));
        assertEquals(1, match.goals(LEFT));
        match.kickOff();
        assertFalse(match.playing());
        assertFalse(match.enterGoal(RIGHT));
    }

    @Test void canWinWithOneGoalButCannotConfigureZeroOrNegativeGoals() {
        var match = new SulfurSoccerMatch(1);
        match.kickOff();
        match.enterGoal(RIGHT);
        assertEquals(LEFT, match.winner());
        assertThrows(IllegalArgumentException.class, () -> new SulfurSoccerMatch(0));
        assertThrows(IllegalArgumentException.class, () -> new SulfurSoccerMatch(-1));
    }
}
