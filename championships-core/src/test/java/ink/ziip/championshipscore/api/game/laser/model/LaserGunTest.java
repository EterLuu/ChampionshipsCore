package ink.ziip.championshipscore.api.game.laser.model;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

class LaserGunTest {
    @Test
    void rifleKeepsThreeBodyHitsThroughMidrangeAndFallsOffBeyondIt() {
        for (double distance : new double[] {0, 15, 16, 30}) {
            assertEquals(34D, LaserGun.RIFLE.damage(LaserGun.HitRegion.BODY, distance));
            assertEquals(
                    3D, Math.ceil(100 / LaserGun.RIFLE.damage(LaserGun.HitRegion.BODY, distance)));
        }
        assertEquals(3D, Math.ceil(100 / LaserGun.RIFLE.damage(LaserGun.HitRegion.BODY, 39)));
        assertEquals(4D, Math.ceil(100 / LaserGun.RIFLE.damage(LaserGun.HitRegion.BODY, 40)));
        assertEquals(30.6D, LaserGun.RIFLE.damage(LaserGun.HitRegion.BODY, 80), 1e-9);
        assertEquals(99D, LaserGun.RIFLE.damage(LaserGun.HitRegion.HEAD, 80), 1e-9);
    }

    @ParameterizedTest
    @CsvSource({
        "SNIPER, GOLDEN_SHOVEL, 2, 80, 2.75, 1, 80, 160, 90, 75",
        "RIFLE, COPPER_HOE, 12, 50, 5.25, 1, 80, 110, 34, 22",
        "SMG, IRON_SPEAR, 35, 40, 15.0, 1, 80, 45, 18, 11",
        "MACHINE, STONE_SPEAR, 60, 70, 15.0, 1, 80, 45, 22, 14",
        "SHOTGUN, DIAMOND_AXE, 7, 50, 3.5, 8, 16, 26, 18, 10"
    })
    void weaponsUseRequestedTransportMagazineCadenceAndPointBlankDamage(
            LaserGun gun,
            Material material,
            int magazine,
            int reloadTicks,
            double shotsPerSecond,
            int pellets,
            int range,
            double head,
            double body,
            double legs) {
        assertEquals(
                List.of(
                        material,
                        magazine,
                        reloadTicks,
                        shotsPerSecond,
                        pellets,
                        range,
                        head,
                        body,
                        legs),
                List.of(
                        gun.material(),
                        gun.magazine(),
                        gun.reloadTicks(),
                        gun.shotsPerSecond(),
                        gun.pellets(),
                        gun.range(),
                        gun.damage(LaserGun.HitRegion.HEAD, 0),
                        gun.damage(LaserGun.HitRegion.BODY, 0),
                        gun.damage(LaserGun.HitRegion.LEGS, 0)));
    }

    @Test
    void distanceFalloffKeepsSniperAndRifleFlatterThanSmg() {
        double sniperLoss =
                LaserGun.SNIPER.damage(LaserGun.HitRegion.BODY, 80)
                        / LaserGun.SNIPER.damage(LaserGun.HitRegion.BODY, 0);
        double rifleLoss =
                LaserGun.RIFLE.damage(LaserGun.HitRegion.BODY, 80)
                        / LaserGun.RIFLE.damage(LaserGun.HitRegion.BODY, 0);
        double smgLoss =
                LaserGun.SMG.damage(LaserGun.HitRegion.BODY, 80)
                        / LaserGun.SMG.damage(LaserGun.HitRegion.BODY, 0);
        double machineLoss =
                LaserGun.MACHINE.damage(LaserGun.HitRegion.BODY, 80)
                        / LaserGun.MACHINE.damage(LaserGun.HitRegion.BODY, 0);
        assertTrue(sniperLoss > rifleLoss);
        assertTrue(rifleLoss > machineLoss);
        assertTrue(machineLoss > smgLoss);
        assertEquals(0.50D, smgLoss, 1.0E-9);
        assertEquals(0.75D, machineLoss, 1.0E-9);
        assertEquals(16.5D, LaserGun.MACHINE.damage(LaserGun.HitRegion.BODY, 80), 1.0E-9D);
        assertEquals(86.4D, LaserGun.SNIPER.damage(LaserGun.HitRegion.BODY, 80), 1.0E-9D);
        assertTrue(LaserGun.SNIPER.damage(LaserGun.HitRegion.HEAD, 80) > 100.0D);
    }

    @Test
    void itemIdsRoundTripWithoutDependingOnDisplayNames() {
        for (LaserGun gun : LaserGun.values()) {
            assertSame(gun, LaserGun.fromId("gun:" + gun.id()));
            assertSame(gun, LaserGun.fromId(gun.id().toUpperCase()));
            assertSame(gun, LaserGun.fromMaterial(gun.material()));
        }
        assertNull(LaserGun.fromId("gun:unknown"));
    }

    @Test
    void burstSpreadStartsAccurateAndReachesWeaponMaximum() {
        assertEquals(0.0D, LaserGun.SNIPER.spreadDegrees(0));
        assertEquals(0.0D, LaserGun.RIFLE.spreadDegrees(0));
        assertEquals(0.75D, LaserGun.RIFLE.spreadDegrees(3));
        assertTrue(
                LaserGun.SMG.spreadDegrees(0) < LaserGun.SMG.spreadDegrees(1)
                        && LaserGun.SMG.spreadDegrees(1) < LaserGun.SMG.spreadDegrees(3));
        assertEquals(7.0D, LaserGun.SHOTGUN.spreadDegrees(0));
    }

    @Test
    void machineGunGetsAnExtraAccuracyBenefitWhileCrouching() {
        double machineStanding = LaserGun.MACHINE.spreadDegrees();
        double rifleStanding = LaserGun.RIFLE.spreadDegrees();

        assertEquals(1.375D, LaserGun.MACHINE.crouchedSpread(machineStanding, false));
        assertEquals(3.5D, LaserGun.MACHINE.crouchedSpread(machineStanding, true));
        assertEquals(
                0.55D,
                LaserGun.MACHINE.crouchedSpread(machineStanding, false) / machineStanding,
                1.0E-9);
        assertEquals(
                0.70D, LaserGun.RIFLE.crouchedSpread(rifleStanding, false) / rifleStanding, 1.0E-9);
    }

    @Test
    void automaticWeaponsUsePredictableFollowUpRecoil() {
        assertEquals(0.0D, LaserGun.RIFLE.recoilPitchDegrees(0));
        assertTrue(LaserGun.RIFLE.recoilPitchDegrees(1) < LaserGun.RIFLE.recoilPitchDegrees(3));
        assertTrue(LaserGun.RIFLE.recoilPitchDegrees(3) >= 0.35D);
        assertTrue(Math.abs(LaserGun.SMG.recoilYawDegrees(1)) >= 0.10D);
        assertNotEquals(LaserGun.SMG.recoilYawDegrees(1), LaserGun.SMG.recoilYawDegrees(2));
        assertEquals(0.0D, LaserGun.SNIPER.recoilPitchDegrees(4));
        assertEquals(0.0D, LaserGun.SHOTGUN.recoilYawDegrees(4));
    }
}
