package ink.ziip.championshipscore.api.game.laser.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.laser.model.LaserGun;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class LaserWeaponStateTest {
    @ParameterizedTest
    @EnumSource(LaserGun.class)
    void emptyMagazineReloadsAtItsOwnDeadline(LaserGun gun) {
        var weapons = new LaserWeaponState();
        UUID player = UUID.randomUUID();
        int shots = 0;
        int tick = 0;
        while (shots < gun.magazine()) {
            if (weapons.shoot(player, gun, tick, true) == LaserWeaponState.FireResult.FIRED)
                shots++;
            if (shots < gun.magazine()) tick++;
        }
        assertEquals(0, weapons.ammo(player, gun));
        assertEquals(gun, weapons.reloadGun(player));
        assertEquals(
                LaserWeaponState.FireResult.RELOADING,
                weapons.shoot(player, gun, tick + gun.reloadTicks() - 1, true));
        assertEquals(
                LaserWeaponState.FireResult.FIRED,
                weapons.shoot(player, gun, tick + gun.reloadTicks(), true));
        assertEquals(gun.magazine() - 1, weapons.ammo(player, gun));
    }

    @Test
    void smgCarriesThirdTickShotDeadlinesAcrossWholeTicks() {
        var weapons = new LaserWeaponState();
        UUID player = UUID.randomUUID();
        List<Integer> fired = new ArrayList<>();
        for (int tick = 0; tick <= 10; tick++)
            if (weapons.shoot(player, LaserGun.SMG, tick, true)
                    == LaserWeaponState.FireResult.FIRED) fired.add(tick);
        assertEquals(List.of(0, 2, 3, 4, 6, 7, 8, 10), fired);
    }

    @ParameterizedTest
    @CsvSource({"SNIPER,11", "RIFLE,21", "SMG,60", "MACHINE,60", "SHOTGUN,14"})
    void uninterruptedFourSecondsUseRequestedRateWithoutWholeTickRounding(
            LaserGun gun, int expected) {
        var weapons = new LaserWeaponState();
        UUID player = UUID.randomUUID();
        int shots = 0;
        for (int tick = 0; tick < 80; tick++) {
            if (weapons.shoot(player, gun, tick, true) == LaserWeaponState.FireResult.FIRED) {
                shots++;
                // Benchmark fire cadence independently of the separately tested reload duration.
                weapons.equip(player, gun);
            }
        }
        assertEquals(expected, shots);
    }

    @Test
    void multipleInputsInTheSameTickCannotBypassCadenceAndLateInputCannotCatchUp() {
        var weapons = new LaserWeaponState();
        UUID player = UUID.randomUUID();
        assertEquals(
                LaserWeaponState.FireResult.FIRED, weapons.shoot(player, LaserGun.SMG, 0, true));
        assertEquals(
                LaserWeaponState.FireResult.COOLDOWN, weapons.shoot(player, LaserGun.SMG, 0, true));
        assertEquals(
                LaserWeaponState.FireResult.FIRED, weapons.shoot(player, LaserGun.SMG, 100, true));
        assertEquals(
                LaserWeaponState.FireResult.COOLDOWN,
                weapons.shoot(player, LaserGun.SMG, 100, true));
        assertEquals(
                LaserWeaponState.FireResult.COOLDOWN,
                weapons.shoot(player, LaserGun.SMG, 101, true));
        assertEquals(
                LaserWeaponState.FireResult.FIRED, weapons.shoot(player, LaserGun.SMG, 102, true));
    }

    @Test
    void manualReloadInterruptionAndWrongHeldGunCannotRefill() {
        var weapons = new LaserWeaponState();
        UUID player = UUID.randomUUID();
        assertFalse(weapons.requestReload(player, LaserGun.RIFLE, 0, true));
        weapons.shoot(player, LaserGun.RIFLE, 0, true);
        assertTrue(weapons.requestReload(player, LaserGun.RIFLE, 1, true));
        assertFalse(weapons.completeReload(player, LaserGun.SMG, 100));
        assertEquals(LaserGun.RIFLE, weapons.cancelReload(player));
        assertFalse(weapons.completeReload(player, LaserGun.RIFLE, 100));
        assertEquals(11, weapons.ammo(player, LaserGun.RIFLE));
    }

    @Test
    void equippingWithinLifePreservesDeadlineButNewLifeClearsIt() {
        var weapons = new LaserWeaponState();
        UUID player = UUID.randomUUID();
        weapons.shoot(player, LaserGun.SNIPER, 0, true);
        weapons.equip(player, LaserGun.RIFLE);
        assertEquals(
                LaserWeaponState.FireResult.COOLDOWN,
                weapons.shoot(player, LaserGun.RIFLE, 1, true));
        weapons.forget(player);
        assertEquals(
                LaserWeaponState.FireResult.FIRED, weapons.shoot(player, LaserGun.RIFLE, 1, true));
        assertEquals(0, weapons.burstShot(player));
    }

    @Test
    void ineligibleCallsAndTerminalMatchCannotRecreateWeaponState() {
        var weapons = new LaserWeaponState();
        UUID player = UUID.randomUUID();
        weapons.shoot(player, LaserGun.RIFLE, 0, true);
        assertEquals(
                LaserWeaponState.FireResult.NOT_READY,
                weapons.shoot(player, LaserGun.RIFLE, 10, false));
        assertFalse(weapons.requestReload(player, LaserGun.RIFLE, 10, false));
        assertEquals(11, weapons.ammo(player, LaserGun.RIFLE));
        weapons.requestReload(player, LaserGun.RIFLE, 10, true);
        weapons.close();
        weapons.equip(player, LaserGun.RIFLE);
        weapons.setAmmo(player, 1);
        assertFalse(weapons.completeReload(player, LaserGun.RIFLE, 100));
        assertFalse(weapons.requestReload(player, LaserGun.RIFLE, 100, true));
        assertEquals(
                LaserWeaponState.FireResult.NOT_READY,
                weapons.shoot(player, LaserGun.RIFLE, 100, true));
        assertEquals(12, weapons.ammo(player, LaserGun.RIFLE));
        assertFalse(weapons.reloading(player));
    }

    @Test
    void playersAndConcurrentMatchesWithTheSameUuidRemainIndependent() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var box = new LaserWeaponState();
        var showdown = new LaserWeaponState();
        box.shoot(first, LaserGun.RIFLE, 0, true);
        box.requestReload(first, LaserGun.RIFLE, 1, true);
        assertEquals(12, box.ammo(second, LaserGun.RIFLE));
        assertEquals(
                LaserWeaponState.FireResult.FIRED, showdown.shoot(first, LaserGun.RIFLE, 1, true));
        box.close();
        assertEquals(11, showdown.ammo(first, LaserGun.RIFLE));
        assertFalse(showdown.reloading(first));
    }
}
