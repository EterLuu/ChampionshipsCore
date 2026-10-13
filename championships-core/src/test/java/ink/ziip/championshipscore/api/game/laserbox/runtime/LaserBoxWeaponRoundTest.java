package ink.ziip.championshipscore.api.game.laserbox.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.laser.model.LaserGun;
import ink.ziip.championshipscore.api.game.laser.runtime.LaserWeaponState;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class LaserBoxWeaponRoundTest {
    @Test
    void eliminatedDisconnectedAndFinishedPlayersCannotFireOrCompleteOldReloads() {
        LaserBoxRound round = new LaserBoxRound();
        UUID player = UUID.randomUUID();
        assertEquals(LaserWeaponState.FireResult.FIRED, round.shoot(player, LaserGun.RIFLE));
        assertTrue(round.requestReload(player, LaserGun.RIFLE));
        round.damage(player, true, true, null, 100);
        assertEquals(LaserWeaponState.FireResult.NOT_READY, round.shoot(player, LaserGun.RIFLE));
        for (int tick = 0; tick < 80; tick++) round.advance();
        assertFalse(round.completeReload(player, LaserGun.RIFLE));
        assertTrue(round.respawn(player));
        assertEquals(LaserWeaponState.FireResult.FIRED, round.shoot(player, LaserGun.RIFLE));
        assertEquals(0, round.burstShot(player));
        round.disconnect(player);
        assertEquals(LaserWeaponState.FireResult.NOT_READY, round.shoot(player, LaserGun.RIFLE));
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
        for (int shot = 0; shot < LaserGun.SNIPER.magazine(); shot++) {
            assertEquals(LaserWeaponState.FireResult.FIRED, round.shoot(player, LaserGun.SNIPER));
            for (int tick = 0; tick < 20; tick++) round.advance();
        }
        assertTrue(round.reloading(player));
        assertEquals(LaserWeaponState.FireResult.RELOADING, round.shoot(player, LaserGun.SNIPER));
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
