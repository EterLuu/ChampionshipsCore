package ink.ziip.championshipscore.api.game.laserbox.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.laserbox.model.LaserBoxGun;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class LaserBoxWeaponRoundTest {
    @Test
    void rifleBodyShotsNeedThreeHitsAndShieldStillConsumesOneShot() {
        LaserBoxRound round = new LaserBoxRound();
        UUID victim = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        round.shield(victim, 1);
        assertEquals(
                LaserBoxRound.DamageResult.SHIELD, round.damage(victim, true, false, attacker, 35));
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
        for (int shot = 0; shot < LaserBoxGun.SNIPER.magazine(); shot++) {
            assertEquals(LaserBoxRound.FireResult.FIRED, round.shoot(player, LaserBoxGun.SNIPER));
            for (int tick = 0; tick < 20; tick++) round.advance();
        }
        assertTrue(round.reloading(player));
        assertEquals(LaserBoxRound.FireResult.RELOADING, round.shoot(player, LaserBoxGun.SNIPER));
        for (int tick = 0; tick < LaserBoxGun.SNIPER.reloadTicks(); tick++) round.advance();
        assertEquals(LaserBoxRound.FireResult.FIRED, round.shoot(player, LaserBoxGun.SNIPER));
        assertEquals(LaserBoxGun.SNIPER.magazine() - 1, round.ammo(player, LaserBoxGun.SNIPER));
    }

    @Test
    void manualReloadStartsOnlyForADepletedMagazine() {
        LaserBoxRound round = new LaserBoxRound();
        UUID player = UUID.randomUUID();
        assertFalse(round.requestReload(player, LaserBoxGun.RIFLE));
        assertEquals(LaserBoxRound.FireResult.FIRED, round.shoot(player, LaserBoxGun.RIFLE));
        assertTrue(round.requestReload(player, LaserBoxGun.RIFLE));
        assertTrue(round.reloading(player));
        assertFalse(round.requestReload(player, LaserBoxGun.RIFLE));
        for (int tick = 0; tick < LaserBoxGun.RIFLE.reloadTicks(); tick++) round.advance();
        assertTrue(round.completeReload(player, LaserBoxGun.RIFLE));
        assertEquals(LaserBoxGun.RIFLE.magazine(), round.ammo(player, LaserBoxGun.RIFLE));
    }

    @Test
    void reloadIsBoundToTheHeldGunAndCanBeInterruptedBeforeItCompletes() {
        LaserBoxRound round = new LaserBoxRound();
        UUID player = UUID.randomUUID();

        assertEquals(LaserBoxRound.FireResult.FIRED, round.shoot(player, LaserBoxGun.RIFLE));
        assertTrue(round.requestReload(player, LaserBoxGun.RIFLE));
        assertEquals(LaserBoxGun.RIFLE, round.reloadGun(player));
        assertEquals(LaserBoxGun.RIFLE, round.cancelReload(player));

        for (int tick = 0; tick < LaserBoxGun.RIFLE.reloadTicks(); tick++) round.advance();
        assertFalse(round.completeReload(player, LaserBoxGun.RIFLE));
        assertEquals(LaserBoxGun.RIFLE.magazine() - 1, round.ammo(player, LaserBoxGun.RIFLE));
    }

    @Test
    void reloadCannotCompleteAgainstADifferentGun() {
        LaserBoxRound round = new LaserBoxRound();
        UUID player = UUID.randomUUID();

        assertEquals(LaserBoxRound.FireResult.FIRED, round.shoot(player, LaserBoxGun.RIFLE));
        assertTrue(round.requestReload(player, LaserBoxGun.RIFLE));
        for (int tick = 0; tick < LaserBoxGun.RIFLE.reloadTicks(); tick++) round.advance();

        assertFalse(round.completeReload(player, LaserBoxGun.SMG));
        assertEquals(LaserBoxGun.RIFLE, round.reloadGun(player));
        assertEquals(LaserBoxGun.RIFLE.magazine() - 1, round.ammo(player, LaserBoxGun.RIFLE));
    }

    @Test
    void burstSpreadResetsAfterAQuietPeriod() {
        LaserBoxRound round = new LaserBoxRound();
        UUID player = UUID.randomUUID();
        assertEquals(LaserBoxRound.FireResult.FIRED, round.shoot(player, LaserBoxGun.RIFLE));
        assertEquals(0.0D, round.spreadDegrees(player, LaserBoxGun.RIFLE));
        for (int tick = 0; tick < 5; tick++) round.advance();
        assertEquals(LaserBoxRound.FireResult.FIRED, round.shoot(player, LaserBoxGun.RIFLE));
        assertEquals(0.25D, round.spreadDegrees(player, LaserBoxGun.RIFLE));
        for (int tick = 0; tick < 8; tick++) round.advance();
        assertEquals(LaserBoxRound.FireResult.FIRED, round.shoot(player, LaserBoxGun.RIFLE));
        assertEquals(0.0D, round.spreadDegrees(player, LaserBoxGun.RIFLE));
    }
}
