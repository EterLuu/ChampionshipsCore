package ink.ziip.championshipscore.api.game.laser.model;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

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

    @Test
    void weaponsExposeTransportRolesAndRequestedMagazineTimings() {
        assertEquals(Material.GOLDEN_SHOVEL, LaserGun.SNIPER.material());
        assertEquals(2, LaserGun.SNIPER.magazine());
        assertEquals(80, LaserGun.SNIPER.reloadTicks());
        assertEquals(2.75D, LaserGun.SNIPER.shotsPerSecond());
        assertEquals(12, LaserGun.RIFLE.magazine());
        assertEquals(50, LaserGun.RIFLE.reloadTicks());
        assertEquals(35, LaserGun.SMG.magazine());
        assertEquals(40, LaserGun.SMG.reloadTicks());
        assertEquals(Material.IRON_SPEAR, LaserGun.SMG.material());
        assertEquals(60, LaserGun.MACHINE.magazine());
        assertEquals(70, LaserGun.MACHINE.reloadTicks());
        assertEquals(Material.STONE_SPEAR, LaserGun.MACHINE.material());
        assertEquals(7, LaserGun.SHOTGUN.magazine());
        assertEquals(50, LaserGun.SHOTGUN.reloadTicks());
        assertEquals(3.5D, LaserGun.SHOTGUN.shotsPerSecond());
        assertEquals(5.25D, LaserGun.RIFLE.shotsPerSecond());
        assertEquals(15.0D, LaserGun.SMG.shotsPerSecond());
        assertEquals(15.0D, LaserGun.MACHINE.shotsPerSecond());
        assertEquals(8, LaserGun.SHOTGUN.pellets());
        assertEquals(80, LaserGun.SMG.range());
        assertEquals(80, LaserGun.MACHINE.range());
        assertEquals(16, LaserGun.SHOTGUN.range());
        assertEquals(22.0D, LaserGun.MACHINE.damage(LaserGun.HitRegion.BODY, 0));
        assertEquals(16.5D, LaserGun.MACHINE.damage(LaserGun.HitRegion.BODY, 80));
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
        assertEquals(86.4D, LaserGun.SNIPER.damage(LaserGun.HitRegion.BODY, 80), 1.0E-9D);
        assertTrue(LaserGun.SNIPER.damage(LaserGun.HitRegion.HEAD, 80) > 100.0D);
    }

    @Test
    void sniperHitsUseRequestedHeadBodyAndLegDamage() {
        assertEquals(160.0D, LaserGun.SNIPER.damage(LaserGun.HitRegion.HEAD, 0));
        assertEquals(90.0D, LaserGun.SNIPER.damage(LaserGun.HitRegion.BODY, 0));
        assertEquals(75.0D, LaserGun.SNIPER.damage(LaserGun.HitRegion.LEGS, 0));
    }

    @Test
    void hitRegionsHaveAClearHeadBodyLegsDamageOrder() {
        for (LaserGun gun : LaserGun.values()) {
            double head = gun.damage(LaserGun.HitRegion.HEAD, 0);
            double body = gun.damage(LaserGun.HitRegion.BODY, 0);
            double legs = gun.damage(LaserGun.HitRegion.LEGS, 0);
            assertTrue(head > body, gun + " head damage should exceed body damage");
            assertTrue(body > legs, gun + " body damage should exceed leg damage");
        }
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
