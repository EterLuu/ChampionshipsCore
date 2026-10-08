package ink.ziip.championshipscore.api.game.laserbox.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class LaserBoxRoundTest {
    @Test
    void transfersChangeAtThirtyAndSixtySecondsAndConserveTotalScore() {
        LaserBoxRound round = new LaserBoxRound();
        assertEquals(50, round.right());
        assertEquals(50, round.left());

        for (int tick = 0; tick < 599; tick++) round.advance();
        assertEquals(5, round.transfer());
        assertTrue(round.hit(UUID.randomUUID(), false, false, null));
        assertEquals(55, round.right());
        assertEquals(45, round.left());

        round.advance();
        assertEquals(7, round.transfer());
        assertTrue(round.hit(UUID.randomUUID(), true, false, null));
        assertEquals(48, round.right());
        assertEquals(52, round.left());

        for (int tick = 0; tick < 599; tick++) round.advance();
        assertEquals(7, round.transfer());
        round.advance();
        assertEquals(10, round.transfer());
        assertTrue(round.hit(UUID.randomUUID(), false, false, null));
        assertEquals(58, round.right());
        assertEquals(42, round.left());
    }

    @Test
    void finalStageTransfersClampBothSidesAtZeroAndOneHundred() {
        for (boolean victimOnRight : new boolean[] {false, true}) {
            LaserBoxRound round = new LaserBoxRound();
            assertTrue(round.hit(UUID.randomUUID(), victimOnRight, false, null));
            for (int tick = 0; tick < 1200; tick++) round.advance();
            // Reach 5/95 before the 10-point final-stage hit, so the remaining 5 points transfer.
            for (int kill = 0; kill < 4; kill++)
                assertTrue(round.hit(UUID.randomUUID(), victimOnRight, false, null));
            assertEquals(victimOnRight ? 5 : 95, round.right());
            assertTrue(round.hit(UUID.randomUUID(), victimOnRight, false, null));
            assertEquals(victimOnRight ? 0 : 100, round.right());
            assertEquals(victimOnRight ? 100 : 0, round.left());
        }
    }

    @Test
    void shieldAbsorbsLaserButGasBypassesIt() {
        LaserBoxRound round = new LaserBoxRound();
        UUID player = UUID.randomUUID();
        round.shield(player, 2);
        assertFalse(round.hit(player, true, false, null));
        assertEquals(1, round.shield(player));
        assertTrue(round.hit(player, true, true, null));
        assertTrue(round.respawning(player));
    }

    @Test
    void introductionEliminationsDoNotTransferMatchScore() {
        LaserBoxRound round = new LaserBoxRound();
        UUID victim = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();

        assertEquals(
                LaserBoxRound.DamageResult.ELIMINATED,
                round.damage(victim, true, true, attacker, 100.0D, false));
        assertEquals(50, round.right());
        assertEquals(50, round.left());
        assertEquals(1, round.kills(attacker));
        assertTrue(round.respawning(victim));
    }

    @Test
    void finishedRoundRejectsFurtherHits() {
        LaserBoxRound round = new LaserBoxRound();
        UUID player = UUID.randomUUID();
        assertTrue(round.finish());
        assertFalse(round.finish());
        assertFalse(round.hit(player, true, false, null));
    }

    @Test
    void respawnGrantsFourSecondsOfSpawnProtection() {
        LaserBoxRound round = new LaserBoxRound();
        UUID player = UUID.randomUUID();
        assertTrue(round.hit(player, true, false, null));
        for (int i = 0; i < 80; i++) round.advance();
        assertTrue(round.respawn(player));
        assertTrue(round.protectedFromHits(player));
        for (int i = 0; i < 79; i++) round.advance();
        assertTrue(round.protectedFromHits(player));
        round.advance();
        assertFalse(round.protectedFromHits(player));
    }

    @Test
    void laserCooldownIsSixAndAQuarterTicks() {
        LaserBoxRound round = new LaserBoxRound();
        UUID player = UUID.randomUUID();
        assertTrue(round.shoot(player));
        for (int i = 0; i < 6; i++) {
            round.advance();
            assertFalse(round.shoot(player));
        }
        round.advance();
        assertTrue(round.shoot(player));
    }

    @Test
    void onlySuccessfulEliminationsCountAsKillsAndRespawnDoesNotResetThem() {
        LaserBoxRound round = new LaserBoxRound();
        UUID attacker = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        round.shield(victim, 2);
        assertFalse(round.hit(victim, false, false, attacker));
        assertFalse(round.hit(victim, false, false, attacker));
        assertEquals(0, round.kills(attacker));
        assertTrue(round.hit(victim, false, false, attacker));
        assertEquals(1, round.kills(attacker));
        assertFalse(round.hit(victim, false, false, attacker));
        assertEquals(1, round.kills(attacker));

        for (int tick = 0; tick < 80; tick++) round.advance();
        assertTrue(round.respawn(victim));
        assertFalse(round.hit(victim, false, false, attacker));
        assertEquals(1, round.kills(attacker));
        for (int tick = 0; tick < 80; tick++) round.advance();
        assertTrue(round.hit(victim, false, false, attacker));
        assertEquals(2, round.kills(attacker));
        round.disconnect(attacker);
        assertEquals(2, round.kills(attacker));
        assertTrue(round.finish());
        assertFalse(round.hit(UUID.randomUUID(), false, false, attacker));
        assertEquals(2, round.kills(attacker));
        assertEquals(0, new LaserBoxRound().kills(attacker));
    }

    @Test
    void environmentalEliminationsAndSelfHitsDoNotCountAsKills() {
        LaserBoxRound round = new LaserBoxRound();
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertTrue(round.hit(player, true, true, null));
        assertEquals(0, round.kills(player));
        assertTrue(round.hit(other, false, false, other));
        assertEquals(0, round.kills(other));
    }
}
