package ink.ziip.championshipscore.api.game.sulfursoccer.mechanics;

import static ink.ziip.championshipscore.api.game.sulfursoccer.model.SulfurSoccerSide.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

class SulfurSoccerShootoutTest {
    private static List<UUID> roster(int size) {
        return IntStream.range(0, size).mapToObj(i -> UUID.randomUUID()).toList();
    }

    @Test
    void eachOfFourPlayersShootsAndKeepsOnceWithAlternatingTeams() {
        var right = roster(4);
        var left = roster(4);
        var shootout = new SulfurSoccerShootout(right, left);
        for (int attempt = 0; attempt < 8; attempt++) {
            boolean rightShoots = attempt % 2 == 0;
            int index = attempt / 2;
            assertEquals(rightShoots ? RIGHT : LEFT, shootout.shootingSide());
            assertEquals((rightShoots ? right : left).get(index), shootout.shooter());
            assertEquals((rightShoots ? left : right).get(index), shootout.keeper());
            assertEquals(index + 1, shootout.round());
            strike(shootout);
            shootout.finishAttempt(rightShoots);
            if (attempt < 7)
                assertNull(shootout.winner(), "All four shots per side must be played");
        }
        assertEquals(4, shootout.attempts(RIGHT));
        assertEquals(4, shootout.attempts(LEFT));
        assertEquals(4, shootout.goals(RIGHT));
        assertEquals(0, shootout.goals(LEFT));
        assertEquals(RIGHT, shootout.winner());
        assertFalse(shootout.strike(shootout.shooter()));
        assertThrows(IllegalStateException.class, () -> shootout.finishAttempt(false));
    }

    @Test
    void tiedInitialFourGoToSuddenDeathAndBothSidesAlwaysGetTheirShot() {
        var right = roster(4);
        var left = roster(4);
        var shootout = new SulfurSoccerShootout(right, left);
        for (int attempt = 0; attempt < 8; attempt++) {
            strike(shootout);
            shootout.finishAttempt(true);
        }
        assertNull(shootout.winner());
        assertEquals(right.getFirst(), shootout.shooter());
        assertEquals(left.getFirst(), shootout.keeper());
        strike(shootout);
        shootout.finishAttempt(true);
        assertNull(shootout.winner());
        strike(shootout);
        shootout.finishAttempt(true);
        assertNull(shootout.winner());
        assertEquals(right.get(1), shootout.shooter());
        shootout.finishAttempt(false);
        assertNull(shootout.winner());
        strike(shootout);
        shootout.finishAttempt(true);
        assertEquals(LEFT, shootout.winner());
        assertEquals(6, shootout.attempts(RIGHT));
        assertEquals(6, shootout.attempts(LEFT));
    }

    @Test
    void supportsSmallerTeamsByCyclingRostersForFourAttemptsPerSide() {
        var right = roster(1);
        var left = roster(2);
        var shootout = new SulfurSoccerShootout(right, left);
        for (int attempt = 0; attempt < 8; attempt++) {
            assertEquals(
                    attempt % 2 == 0 ? right.getFirst() : left.get((attempt / 2) % 2),
                    shootout.shooter());
            assertEquals(
                    attempt % 2 == 0 ? left.get((attempt / 2) % 2) : right.getFirst(),
                    shootout.keeper());
            shootout.finishAttempt(false);
        }
        assertEquals(4, shootout.attempts(RIGHT));
        assertEquals(4, shootout.attempts(LEFT));
    }

    @Test
    void shooterGetsExactlyOneAttackAndKeeperOrOtherPlayersCannotShoot() {
        var right = roster(4);
        var left = roster(4);
        var shootout = new SulfurSoccerShootout(right, left);
        assertFalse(
                shootout.strike(shootout.shooter()),
                "No attack during the three-second preparation");
        prepare(shootout);
        assertFalse(shootout.strike(shootout.keeper()));
        assertFalse(shootout.strike(right.get(1)));
        assertTrue(shootout.strike(shootout.shooter()));
        assertFalse(shootout.strike(shootout.shooter()));
        assertFalse(shootout.canStrike(shootout.shooter()));
    }

    @Test
    void unattemptedAndFlyingShotsHaveExactDeadlinesAndDoNotAwardGoals() {
        var shootout = new SulfurSoccerShootout(roster(1), roster(1));
        for (int tick = 0; tick < 359; tick++) assertFalse(shootout.tick());
        assertEquals(1, shootout.secondsRemaining());
        assertTrue(shootout.tick());
        shootout.finishAttempt(false);
        assertEquals(0, shootout.goals(RIGHT));
        assertThrows(IllegalStateException.class, () -> shootout.finishAttempt(true));
        strike(shootout);
        for (int tick = 0; tick < 159; tick++) assertFalse(shootout.tick());
        assertEquals(1, shootout.secondsRemaining());
        assertTrue(shootout.tick());
        shootout.finishAttempt(false);
        assertEquals(0, shootout.goals(LEFT));
    }

    private static void prepare(SulfurSoccerShootout shootout) {
        for (int tick = 0; tick < SulfurSoccerShootout.PREPARATION_TICKS; tick++)
            assertFalse(shootout.tick());
    }

    private static void strike(SulfurSoccerShootout shootout) {
        prepare(shootout);
        assertTrue(shootout.strike(shootout.shooter()));
    }
}
