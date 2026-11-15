package ink.ziip.championshipscore.api.game.laserbox.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.laser.model.LaserGun;
import ink.ziip.championshipscore.api.game.laser.runtime.LaserWeaponState;

import org.junit.jupiter.api.Nested;
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

    /** Per-life gun selection windows owned by the round. */
    @Nested
    class GunSelection {
        @Test
        void eachRespawnGetsEightSecondsAndOldLifeCannotChangeTheNewWindow() {
            var round = new LaserBoxRound();
            UUID player = UUID.randomUUID();
            round.beginLife(player);
            var first = round.selection(player);
            for (int tick = 0; tick < 159; tick++) round.advance();
            assertTrue(first.canSelect(round.tick()));
            round.beginLife(player);
            assertSame(first, round.selection(player), "repeated setup cannot renew the window");
            round.advance();
            assertFalse(first.canSelect(round.tick()));
            round.damage(player, true, true, null, 100);
            for (int tick = 0; tick < 80; tick++) round.advance();
            assertTrue(round.respawn(player));
            var next = round.selection(player);
            assertNotSame(first, next);
            first.closeMenu(round.tick());
            assertTrue(next.canSelect(round.tick() + 159));
            assertFalse(next.canSelect(round.tick() + 160));
        }

        @Test
        void onlySuccessfulFireAndActualDamageLockSelection() {
            var round = new LaserBoxRound();
            UUID shooter = UUID.randomUUID(), victim = UUID.randomUUID();
            round.beginLife(shooter);
            assertFalse(round.requestReload(shooter, LaserGun.RIFLE));
            assertTrue(round.selection(shooter).canSelect(0));
            assertEquals(LaserWeaponState.FireResult.FIRED, round.shoot(shooter, LaserGun.RIFLE));
            assertTrue(round.selection(shooter).locked());
            round.beginLife(victim);
            round.protect(victim);
            assertEquals(
                    LaserBoxRound.DamageResult.IGNORED,
                    round.damage(victim, true, false, shooter, 35));
            assertFalse(round.selection(victim).locked());
            for (int tick = 0; tick < 80; tick++) round.advance();
            round.shield(victim, 1);
            assertEquals(
                    LaserBoxRound.DamageResult.SHIELD,
                    round.damage(victim, true, false, shooter, 35));
            assertFalse(round.selection(victim).locked());
            assertEquals(
                    LaserBoxRound.DamageResult.DAMAGED,
                    round.damage(victim, true, false, shooter, 35));
            assertTrue(round.selection(victim).locked());
        }

        @Test
        void disconnectAndFinishInvalidateOpenMenusWithoutRecreatingTheirLife() {
            var round = new LaserBoxRound();
            UUID player = UUID.randomUUID();
            round.beginLife(player);
            var old = round.selection(player);
            assertTrue(old.openMenu(0));
            round.disconnect(player);
            assertFalse(old.canSelect(1));
            round.beginLife(player);
            assertSame(old, round.selection(player));
            round.finish();
            assertNull(round.selection(player));
            round.beginLife(player);
            assertNull(round.selection(player));
        }
    }

    /** Magazine, reload, cadence and protection rules applied through the round. */
    @Nested
    class Weapons {
        @Test
        void rifleCooldownUsesTheSharedFractionalDeadline() {
            LaserBoxRound round = new LaserBoxRound();
            UUID player = UUID.randomUUID();
            assertEquals(LaserWeaponState.FireResult.FIRED, round.shoot(player, LaserGun.RIFLE));
            for (int i = 0; i < 3; i++) {
                round.advance();
                assertEquals(
                        LaserWeaponState.FireResult.COOLDOWN, round.shoot(player, LaserGun.RIFLE));
            }
            round.advance();
            assertEquals(LaserWeaponState.FireResult.FIRED, round.shoot(player, LaserGun.RIFLE));
        }

        @Test
        void firingThroughoutRespawnProtectionDoesNotCancelOrExtendIt() {
            LaserBoxRound round = new LaserBoxRound();
            UUID player = UUID.randomUUID(), enemy = UUID.randomUUID();
            round.protect(player);
            for (int tick = 0; tick < 80; tick++) {
                round.shoot(player, LaserGun.MACHINE);
                assertTrue(round.protectedFromHits(player));
                assertEquals(
                        LaserBoxRound.DamageResult.IGNORED,
                        round.damage(player, true, false, enemy, 100));
                round.advance();
            }
            assertFalse(round.protectedFromHits(player));
            assertEquals(
                    LaserBoxRound.DamageResult.ELIMINATED,
                    round.damage(player, true, false, enemy, 100));
        }

        @Test
        void eliminatedDisconnectedAndFinishedPlayersCannotFireOrCompleteOldReloads() {
            LaserBoxRound round = new LaserBoxRound();
            UUID player = UUID.randomUUID();
            assertEquals(LaserWeaponState.FireResult.FIRED, round.shoot(player, LaserGun.RIFLE));
            assertTrue(round.requestReload(player, LaserGun.RIFLE));
            round.damage(player, true, true, null, 100);
            assertEquals(
                    LaserWeaponState.FireResult.NOT_READY, round.shoot(player, LaserGun.RIFLE));
            for (int tick = 0; tick < 80; tick++) round.advance();
            assertFalse(round.completeReload(player, LaserGun.RIFLE));
            assertTrue(round.respawn(player));
            assertEquals(LaserWeaponState.FireResult.FIRED, round.shoot(player, LaserGun.RIFLE));
            assertEquals(0, round.burstShot(player));
            round.disconnect(player);
            assertEquals(
                    LaserWeaponState.FireResult.NOT_READY, round.shoot(player, LaserGun.RIFLE));
            assertTrue(round.finish());
            assertFalse(round.requestReload(UUID.randomUUID(), LaserGun.RIFLE));
            assertFalse(round.respawn(player));
        }

        @Test
        void rifleBodyShotsNeedThreeHitsAndShieldStillConsumesOneShot() {
            LaserBoxRound round = new LaserBoxRound();
            UUID victim = UUID.randomUUID();
            UUID attacker = UUID.randomUUID();
            round.shield(victim, 1);
            assertEquals(
                    LaserBoxRound.DamageResult.SHIELD,
                    round.damage(victim, true, false, attacker, 35));
            assertEquals(100.0D, round.health(victim));
            assertEquals(
                    LaserBoxRound.DamageResult.DAMAGED,
                    round.damage(victim, true, false, attacker, 35));
            assertEquals(
                    LaserBoxRound.DamageResult.DAMAGED,
                    round.damage(victim, true, false, attacker, 35));
            assertEquals(
                    LaserBoxRound.DamageResult.ELIMINATED,
                    round.damage(victim, true, false, attacker, 35));
            assertEquals(1, round.kills(attacker));
        }

        @Test
        void magazineAutomaticallyLocksTheWeaponUntilReloadDeadline() {
            LaserBoxRound round = new LaserBoxRound();
            UUID player = UUID.randomUUID();
            for (int shot = 0; shot < LaserGun.SNIPER.magazine(); shot++) {
                assertEquals(
                        LaserWeaponState.FireResult.FIRED, round.shoot(player, LaserGun.SNIPER));
                for (int tick = 0; tick < 20; tick++) round.advance();
            }
            assertTrue(round.reloading(player));
            assertEquals(
                    LaserWeaponState.FireResult.RELOADING, round.shoot(player, LaserGun.SNIPER));
            for (int tick = 0; tick < LaserGun.SNIPER.reloadTicks(); tick++) round.advance();
            assertEquals(LaserWeaponState.FireResult.FIRED, round.shoot(player, LaserGun.SNIPER));
            assertEquals(LaserGun.SNIPER.magazine() - 1, round.ammo(player, LaserGun.SNIPER));
        }

        @Test
        void manualReloadStartsOnlyForADepletedMagazine() {
            LaserBoxRound round = new LaserBoxRound();
            UUID player = UUID.randomUUID();
            assertFalse(round.requestReload(player, LaserGun.RIFLE));
            assertEquals(LaserWeaponState.FireResult.FIRED, round.shoot(player, LaserGun.RIFLE));
            assertTrue(round.requestReload(player, LaserGun.RIFLE));
            assertTrue(round.reloading(player));
            assertFalse(round.requestReload(player, LaserGun.RIFLE));
            for (int tick = 0; tick < LaserGun.RIFLE.reloadTicks(); tick++) round.advance();
            assertTrue(round.completeReload(player, LaserGun.RIFLE));
            assertEquals(LaserGun.RIFLE.magazine(), round.ammo(player, LaserGun.RIFLE));
        }

        @Test
        void reloadIsBoundToTheHeldGunAndCanBeInterruptedBeforeItCompletes() {
            LaserBoxRound round = new LaserBoxRound();
            UUID player = UUID.randomUUID();

            assertEquals(LaserWeaponState.FireResult.FIRED, round.shoot(player, LaserGun.RIFLE));
            assertTrue(round.requestReload(player, LaserGun.RIFLE));
            assertEquals(LaserGun.RIFLE, round.reloadGun(player));
            assertEquals(LaserGun.RIFLE, round.cancelReload(player));

            for (int tick = 0; tick < LaserGun.RIFLE.reloadTicks(); tick++) round.advance();
            assertFalse(round.completeReload(player, LaserGun.RIFLE));
            assertEquals(LaserGun.RIFLE.magazine() - 1, round.ammo(player, LaserGun.RIFLE));
        }

        @Test
        void reloadCannotCompleteAgainstADifferentGun() {
            LaserBoxRound round = new LaserBoxRound();
            UUID player = UUID.randomUUID();

            assertEquals(LaserWeaponState.FireResult.FIRED, round.shoot(player, LaserGun.RIFLE));
            assertTrue(round.requestReload(player, LaserGun.RIFLE));
            for (int tick = 0; tick < LaserGun.RIFLE.reloadTicks(); tick++) round.advance();

            assertFalse(round.completeReload(player, LaserGun.SMG));
            assertEquals(LaserGun.RIFLE, round.reloadGun(player));
            assertEquals(LaserGun.RIFLE.magazine() - 1, round.ammo(player, LaserGun.RIFLE));
        }

        @Test
        void burstSpreadResetsAfterAQuietPeriod() {
            LaserBoxRound round = new LaserBoxRound();
            UUID player = UUID.randomUUID();
            assertEquals(LaserWeaponState.FireResult.FIRED, round.shoot(player, LaserGun.RIFLE));
            assertEquals(0.0D, round.spreadDegrees(player, LaserGun.RIFLE));
            for (int tick = 0; tick < 5; tick++) round.advance();
            assertEquals(LaserWeaponState.FireResult.FIRED, round.shoot(player, LaserGun.RIFLE));
            assertEquals(0.25D, round.spreadDegrees(player, LaserGun.RIFLE));
            for (int tick = 0; tick < 8; tick++) round.advance();
            assertEquals(LaserWeaponState.FireResult.FIRED, round.shoot(player, LaserGun.RIFLE));
            assertEquals(0.0D, round.spreadDegrees(player, LaserGun.RIFLE));
        }
    }
}
