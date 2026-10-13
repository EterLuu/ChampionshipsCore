package ink.ziip.championshipscore.api.game.laser.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.laser.model.LaserGun;

import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

class LaserGunMechanicsTest {
    @Test
    void smgRetainsFractionalTickRemaindersWhileRecoveringFromLateInput() {
        Double deadline = null;
        List<Integer> fired = new ArrayList<>();
        for (int tick = 0; tick <= 10; tick++) {
            int now = tick;
            if (deadline == null || deadline - now <= 1.0E-9D) {
                fired.add(tick);
                deadline = LaserGunMechanics.nextShot(now, deadline, LaserGun.SMG);
            }
        }
        assertEquals(List.of(0, 2, 3, 4, 6, 7, 8, 10), fired);
        assertEquals(101.333333333D, LaserGunMechanics.nextShot(100, 10.0D, LaserGun.SMG), 1.0E-9D);
    }

    @Test
    void sniperUsesSlownessOneWhileHeldAndSlownessThreeWhileAiming() {
        assertEquals(0, LaserGunMechanics.slownessAmplifier(LaserGun.SNIPER, false));
        assertEquals(2, LaserGunMechanics.slownessAmplifier(LaserGun.SNIPER, true));
    }

    @Test
    void firstShotAndQuietPeriodResetBurstWithoutSentinelOverflow() {
        assertEquals(0, LaserGunMechanics.burstShot(0, null, 0));
        assertEquals(0, LaserGunMechanics.burstShot(36000, null, 9));
        assertEquals(3, LaserGunMechanics.burstShot(31, 0, 2));
        assertEquals(0, LaserGunMechanics.burstShot(32, 0, 2));
    }

    @Test
    void sniperAimReducesSpreadInEveryPosture() {
        assertEquals(
                0.025,
                LaserGunMechanics.combatSpread(LaserGun.SNIPER, 3, true, false, false, true),
                1e-9);
        assertEquals(
                0.0175,
                LaserGunMechanics.combatSpread(LaserGun.SNIPER, 3, true, true, false, true),
                1e-9);
        assertEquals(
                0.11375,
                LaserGunMechanics.combatSpread(LaserGun.SNIPER, 3, true, true, true, true),
                1e-9);
        assertEquals(
                0.169,
                LaserGunMechanics.combatSpread(LaserGun.SNIPER, 3, true, false, true, true),
                1e-9);
        assertEquals(
                0.425,
                LaserGunMechanics.combatSpread(LaserGun.SNIPER, 3, false, true, true, true),
                1e-9);
        assertEquals(
                0, LaserGunMechanics.combatSpread(LaserGun.SNIPER, 0, true, false, false, true));
    }

    @Test
    void aimingDoesNotChangeOtherWeaponsAndMachineCrouchingRetainsItsBenefit() {
        for (LaserGun gun : LaserGun.values()) {
            if (gun == LaserGun.SNIPER) continue;
            for (boolean grounded : new boolean[] {false, true})
                for (boolean moving : new boolean[] {false, true})
                    assertEquals(
                            LaserGunMechanics.combatSpread(gun, 3, grounded, true, moving, false),
                            LaserGunMechanics.combatSpread(gun, 3, grounded, true, moving, true));
        }
        assertEquals(
                1.375,
                LaserGunMechanics.combatSpread(LaserGun.MACHINE, 3, true, true, false, false));
    }

    @Test
    void unscopedSniperAddsSixDegreesEvenToFirstShotsAndCrouching() {
        for (int burst : new int[] {0, 3})
            for (boolean grounded : new boolean[] {false, true})
                for (boolean crouched : new boolean[] {false, true})
                    for (boolean moving : new boolean[] {false, true}) {
                        double hip =
                                LaserGunMechanics.combatSpread(
                                        LaserGun.SNIPER, burst, grounded, crouched, moving, false);
                        double scoped =
                                LaserGunMechanics.combatSpread(
                                        LaserGun.SNIPER, burst, grounded, crouched, moving, true);
                        assertTrue(hip >= 6.0D);
                        assertTrue(scoped <= 0.425D + 1.0E-9D);
                        assertTrue(hip > scoped * 10.0D);
                    }
        assertEquals(
                6.0D,
                LaserGunMechanics.combatSpread(LaserGun.SNIPER, 0, true, false, false, false));
        assertEquals(
                6.1D,
                LaserGunMechanics.combatSpread(LaserGun.SNIPER, 3, true, false, false, false));
    }

    @Test
    void onlySmgAndMachineAreAutomaticAndSniperHasNoSpeedBoost() {
        assertTrue(LaserGunMechanics.automatic(LaserGun.SMG));
        assertTrue(LaserGunMechanics.automatic(LaserGun.MACHINE));
        assertFalse(LaserGunMechanics.automatic(LaserGun.RIFLE));
        assertFalse(LaserGunMechanics.automatic(LaserGun.SHOTGUN));
        assertFalse(LaserGunMechanics.automatic(LaserGun.SNIPER));
        assertFalse(LaserGunMechanics.automatic(null));
        assertEquals(-1, LaserGunMechanics.speedAmplifier(LaserGun.SNIPER, false));
        assertEquals(-1, LaserGunMechanics.speedAmplifier(LaserGun.MACHINE, false));
        assertEquals(1, LaserGunMechanics.speedAmplifier(LaserGun.SMG, false));
        assertEquals(1, LaserGunMechanics.speedAmplifier(null, true));
        assertEquals(0, LaserGunMechanics.speedAmplifier(null, false));
        assertEquals(-1, LaserGunMechanics.speedAmplifier(LaserGun.RIFLE, false));
        assertEquals(0, LaserGunMechanics.speedAmplifier(LaserGun.SHOTGUN, false));
        assertEquals(0, LaserGunMechanics.slownessAmplifier(LaserGun.MACHINE, false));
        assertEquals(0, LaserGunMechanics.slownessAmplifier(LaserGun.MACHINE, true));
        for (LaserGun gun : List.of(LaserGun.RIFLE, LaserGun.SHOTGUN, LaserGun.SMG)) {
            assertEquals(-1, LaserGunMechanics.slownessAmplifier(gun, false), gun.id());
            assertEquals(-1, LaserGunMechanics.slownessAmplifier(gun, true), gun.id());
        }
        assertEquals(-1, LaserGunMechanics.slownessAmplifier(null, false));
    }

    @Test
    void hitRegionUsesCurrentBoundingBoxIncludingCrouchedHeight() {
        for (double height : new double[] {1.8, 1.5}) {
            Player victim =
                    (Player)
                            Proxy.newProxyInstance(
                                    Player.class.getClassLoader(),
                                    new Class<?>[] {Player.class},
                                    (p, m, a) -> new BoundingBox(0, 64, 0, 0.6, 64 + height, 0.6));
            assertEquals(
                    LaserGun.HitRegion.HEAD,
                    LaserGunMechanics.hitRegion(victim, new Vector(0, 64 + height * 0.8, 0)));
            assertEquals(
                    LaserGun.HitRegion.BODY,
                    LaserGunMechanics.hitRegion(victim, new Vector(0, 64 + height * 0.5, 0)));
            assertEquals(
                    LaserGun.HitRegion.LEGS,
                    LaserGunMechanics.hitRegion(victim, new Vector(0, 64 + height * 0.2, 0)));
        }
    }

    @Test
    void verticalAimAndFollowUpRecoilRemainFiniteAndNormalized() {
        for (LaserGun gun : LaserGun.values()) {
            for (Vector aim :
                    List.of(new Vector(0, 1, 0), new Vector(0, -1, 0), new Vector(0, 0, 1))) {
                Vector original = aim.clone();
                Vector recoil = LaserGunMechanics.recoil(aim, gun, 3);
                assertEquals(1.0, recoil.length(), 1e-9);
                assertEquals(original, aim);
                Vector shot = LaserGunMechanics.spread(recoil, gun.spreadDegrees());
                assertEquals(1.0, shot.length(), 1e-9);
            }
        }
    }
}
