package ink.ziip.championshipscore.api.game.laserbox.model;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class LaserBoxGunTest {
    @Test
    void weaponsExposeTransportRolesAndRequestedMagazineTimings() {
        assertEquals(Material.GOLDEN_SHOVEL, LaserBoxGun.SNIPER.material());
        assertEquals(5, LaserBoxGun.SNIPER.magazine());
        assertEquals(80, LaserBoxGun.SNIPER.reloadTicks());
        assertEquals(80, LaserBoxGun.SNIPER.fireIntervalQuarterTicks());
        assertEquals(12, LaserBoxGun.RIFLE.magazine());
        assertEquals(50, LaserBoxGun.RIFLE.reloadTicks());
        assertEquals(30, LaserBoxGun.SMG.magazine());
        assertEquals(40, LaserBoxGun.SMG.reloadTicks());
        assertEquals(Material.IRON_SPEAR, LaserBoxGun.SMG.material());
        assertEquals(50, LaserBoxGun.MACHINE.magazine());
        assertEquals(70, LaserBoxGun.MACHINE.reloadTicks());
        assertEquals(Material.STONE_SPEAR, LaserBoxGun.MACHINE.material());
        assertEquals(3, LaserBoxGun.SHOTGUN.magazine());
        assertEquals(50, LaserBoxGun.SHOTGUN.reloadTicks());
        assertEquals(40, LaserBoxGun.SHOTGUN.fireIntervalQuarterTicks());
        assertEquals(8, LaserBoxGun.SHOTGUN.pellets());
        assertEquals(80, LaserBoxGun.SMG.range());
        assertEquals(80, LaserBoxGun.MACHINE.range());
        assertEquals(16, LaserBoxGun.SHOTGUN.range());
        assertEquals(22.0D, LaserBoxGun.MACHINE.damage(LaserBoxGun.HitRegion.BODY, 0));
        assertEquals(16.5D, LaserBoxGun.MACHINE.damage(LaserBoxGun.HitRegion.BODY, 80));
    }

    @Test
    void distanceFalloffKeepsSniperAndRifleFlatterThanSmg() {
        double sniperLoss =
                LaserBoxGun.SNIPER.damage(LaserBoxGun.HitRegion.BODY, 80)
                        / LaserBoxGun.SNIPER.damage(LaserBoxGun.HitRegion.BODY, 0);
        double rifleLoss =
                LaserBoxGun.RIFLE.damage(LaserBoxGun.HitRegion.BODY, 80)
                        / LaserBoxGun.RIFLE.damage(LaserBoxGun.HitRegion.BODY, 0);
        double smgLoss =
                LaserBoxGun.SMG.damage(LaserBoxGun.HitRegion.BODY, 80)
                        / LaserBoxGun.SMG.damage(LaserBoxGun.HitRegion.BODY, 0);
        double machineLoss =
                LaserBoxGun.MACHINE.damage(LaserBoxGun.HitRegion.BODY, 80)
                        / LaserBoxGun.MACHINE.damage(LaserBoxGun.HitRegion.BODY, 0);
        assertTrue(sniperLoss > rifleLoss);
        assertTrue(rifleLoss > machineLoss);
        assertTrue(machineLoss > smgLoss);
        assertEquals(0.50D, smgLoss, 1.0E-9);
        assertEquals(0.75D, machineLoss, 1.0E-9);
        assertTrue(LaserBoxGun.SNIPER.damage(LaserBoxGun.HitRegion.BODY, 80) >= 100.0D);
    }

    @Test
    void hitRegionsHaveAClearHeadBodyLegsDamageOrder() {
        for (LaserBoxGun gun : LaserBoxGun.values()) {
            double head = gun.damage(LaserBoxGun.HitRegion.HEAD, 0);
            double body = gun.damage(LaserBoxGun.HitRegion.BODY, 0);
            double legs = gun.damage(LaserBoxGun.HitRegion.LEGS, 0);
            assertTrue(head > body, gun + " head damage should exceed body damage");
            assertTrue(body > legs, gun + " body damage should exceed leg damage");
        }
    }

    @Test
    void itemIdsRoundTripWithoutDependingOnDisplayNames() {
        for (LaserBoxGun gun : LaserBoxGun.values()) {
            assertSame(gun, LaserBoxGun.fromId("gun:" + gun.id()));
            assertSame(gun, LaserBoxGun.fromId(gun.id().toUpperCase()));
            assertSame(gun, LaserBoxGun.fromMaterial(gun.material()));
        }
        assertNull(LaserBoxGun.fromId("gun:unknown"));
    }

    @Test
    void burstSpreadStartsAccurateAndReachesWeaponMaximum() {
        assertEquals(0.0D, LaserBoxGun.SNIPER.spreadDegrees(0));
        assertEquals(0.0D, LaserBoxGun.RIFLE.spreadDegrees(0));
        assertEquals(0.75D, LaserBoxGun.RIFLE.spreadDegrees(3));
        assertTrue(
                LaserBoxGun.SMG.spreadDegrees(0) < LaserBoxGun.SMG.spreadDegrees(1)
                        && LaserBoxGun.SMG.spreadDegrees(1) < LaserBoxGun.SMG.spreadDegrees(3));
        assertEquals(7.0D, LaserBoxGun.SHOTGUN.spreadDegrees(0));
    }

    @Test
    void machineGunGetsAnExtraAccuracyBenefitWhileCrouching() {
        double machineStanding = LaserBoxGun.MACHINE.spreadDegrees();
        double rifleStanding = LaserBoxGun.RIFLE.spreadDegrees();

        assertEquals(1.375D, LaserBoxGun.MACHINE.crouchedSpread(machineStanding, false));
        assertEquals(3.5D, LaserBoxGun.MACHINE.crouchedSpread(machineStanding, true));
        assertEquals(
                0.55D,
                LaserBoxGun.MACHINE.crouchedSpread(machineStanding, false) / machineStanding,
                1.0E-9);
        assertEquals(
                0.70D,
                LaserBoxGun.RIFLE.crouchedSpread(rifleStanding, false) / rifleStanding,
                1.0E-9);
    }

    @Test
    void automaticWeaponsUsePredictableFollowUpRecoil() {
        assertEquals(0.0D, LaserBoxGun.RIFLE.recoilPitchDegrees(0));
        assertTrue(
                LaserBoxGun.RIFLE.recoilPitchDegrees(1) < LaserBoxGun.RIFLE.recoilPitchDegrees(3));
        assertTrue(LaserBoxGun.RIFLE.recoilPitchDegrees(3) >= 0.35D);
        assertTrue(Math.abs(LaserBoxGun.SMG.recoilYawDegrees(1)) >= 0.10D);
        assertNotEquals(LaserBoxGun.SMG.recoilYawDegrees(1), LaserBoxGun.SMG.recoilYawDegrees(2));
        assertEquals(0.0D, LaserBoxGun.SNIPER.recoilPitchDegrees(4));
        assertEquals(0.0D, LaserBoxGun.SHOTGUN.recoilYawDegrees(4));
    }
}
