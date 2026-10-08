package ink.ziip.championshipscore.api.game.acerace.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class AceRaceAreaTest {

    @Test
    void completionUsesOnlyTheNonemptyCurrentRoster() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID departed = UUID.randomUUID();
        assertFalse(AceRaceArea.allParticipantsFinished(List.of(first, second), List.of(first)));
        assertTrue(
                AceRaceArea.allParticipantsFinished(
                        List.of(first, second), List.of(first, second)));
        assertTrue(
                AceRaceArea.allParticipantsFinished(
                        List.of(first, first), List.of(departed, first)));
        assertFalse(AceRaceArea.allParticipantsFinished(List.of(), List.of(departed)));
    }

    @Test
    void keepsRedAndOrangePadBaseMomentumDistinct() {
        assertEquals(2D, AceRaceArea.launchHorizontalVelocity(Material.RED_WOOL));
        assertEquals(0.75D, AceRaceArea.launchBaseVerticalVelocity(Material.RED_WOOL));
        assertEquals(4D, AceRaceArea.launchHorizontalVelocity(Material.ORANGE_WOOL));
        assertEquals(1.5D, AceRaceArea.launchBaseVerticalVelocity(Material.ORANGE_WOOL));
    }

    @Test
    void aimedLiftKeepsBaseAndScalingWithinBothClamps() {
        assertEquals(0.9D, AceRaceArea.calculateAimedVerticalVelocity(1D, -1D));
        assertEquals(1D, AceRaceArea.calculateAimedVerticalVelocity(1D, 0D));
        assertEquals(1.1D, AceRaceArea.calculateAimedVerticalVelocity(1D, 0.1D));
        assertEquals(1.2D, AceRaceArea.calculateAimedVerticalVelocity(1D, 1D));
        assertEquals(
                0.675D,
                AceRaceArea.calculateAimedVerticalVelocity(
                        AceRaceArea.launchBaseVerticalVelocity(Material.RED_WOOL), -1D));
    }

    /**
     * Replays a client's horizontal speed after a pad impulse. The only difference between an
     * honest and an exploited launch is the drag paid on the launch tick: {@code 0.6 * 0.91} while
     * still standing on the wool versus {@code 0.91} once the racer jumped clear of it. Both racers
     * hold forward, so both carry the same sprint air acceleration.
     */
    private static double branchSpeed(
            double horizontalVelocity, int ticks, double launchTickDrag, double airAcceleration) {
        double speed = horizontalVelocity;
        for (int tick = 1; tick <= ticks; tick++) {
            speed = speed * (tick == 1 ? launchTickDrag : 0.91D) + airAcceleration;
        }
        return speed;
    }

    private static double groundedBranchSpeed(double horizontalVelocity, int ticks) {
        return branchSpeed(horizontalVelocity, ticks, 0.6D * 0.91D, 0.026D);
    }

    /**
     * Same launch, but applied after the racer left the ground: the branch this fix exists to
     * remove.
     */
    private static double airborneBranchSpeed(double horizontalVelocity, int ticks) {
        return branchSpeed(horizontalVelocity, ticks, 0.91D, 0.026D);
    }

    /** An honest racer who never touches the movement keys, the slowest legitimate flight. */
    private static double coastingBranchSpeed(double horizontalVelocity, int ticks) {
        return branchSpeed(horizontalVelocity, ticks, 0.6D * 0.91D, 0D);
    }

    @Test
    void launchEnvelopeTracksBothPadsWithoutPenalizingHonestFlight() {
        for (Material pad : List.of(Material.RED_WOOL, Material.ORANGE_WOOL)) {
            double horizontal = AceRaceArea.launchHorizontalVelocity(pad);
            assertEquals(horizontal, AceRaceArea.launchEnvelopeSpeed(horizontal, 0), pad.name());
            assertEquals(horizontal, AceRaceArea.launchEnvelopeDistance(horizontal, 0), pad.name());
            double sprinting = horizontal;
            double coasting = horizontal;
            double previousBudget = horizontal;
            for (int tick = 1; tick <= 20; tick++) {
                String context = pad + " tick=" + tick;
                double grounded = groundedBranchSpeed(horizontal, tick);
                double envelope = AceRaceArea.launchEnvelopeSpeed(horizontal, tick);
                assertEquals(grounded, envelope, 1e-9D, context);
                assertTrue(envelope < airborneBranchSpeed(horizontal, tick), context + " exploit");
                sprinting += grounded;
                coasting += coastingBranchSpeed(horizontal, tick);
                double budget = AceRaceArea.launchEnvelopeDistance(horizontal, tick);
                assertTrue(sprinting <= budget, context + " sprinting");
                assertTrue(coasting <= budget, context + " coasting");
                assertTrue(budget > previousBudget, context + " distance must increase");
                previousBudget = budget;
            }
        }
        assertEquals(1.118D, AceRaceArea.launchEnvelopeSpeed(2D, 1), 1e-9D);
    }

    @Test
    void jumpExploitExceedsTheDistanceBudgetWithinThreeTicks() {
        double horizontal = AceRaceArea.launchHorizontalVelocity(Material.RED_WOOL);
        double exploited = horizontal;
        double budget = AceRaceArea.launchEnvelopeDistance(horizontal, 3);
        for (int tick = 1; tick <= 3; tick++) exploited += airborneBranchSpeed(horizontal, tick);
        // The 0.5 block tolerance that absorbs movement-packet jitter must not hide the exploit.
        assertTrue(
                exploited - budget > 0.5D,
                "exploited " + exploited + " should clear budget " + budget + " plus tolerance");
    }

    @Test
    void excessDistanceReducesSpeedWithoutReversingOrPenalizingDebtFreeFlight() {
        double[][] cases = {
            {1.5, 0, 1.5}, {1.5, -3, 1.5}, {1.5, 3, 1}, {1.5, 0.6, 1.4}, {0.4, 100, 0}
        };
        for (double[] example : cases) {
            assertEquals(
                    example[2],
                    AceRaceArea.enforcedLaunchSpeed(example[0], example[1]),
                    1e-9D,
                    "speed=" + example[0] + " debt=" + example[1]);
        }
    }

    @Test
    void horizontalCorrectionKeepsServerVerticalVelocity() {
        Vector corrected =
                AceRaceArea.rescaleHorizontalVelocity(new Vector(1.4D, 0.37D, 0D), 2D, 0D, 2D, 1D);

        assertEquals(1D, corrected.getX(), 1e-9D);
        assertEquals(
                0.37D,
                corrected.getY(),
                1e-9D,
                "a movement displacement must never be reused as the vertical velocity");
        assertEquals(0D, corrected.getZ(), 1e-9D);
    }

    @Test
    void delayedLaunchRequiresAValidGroundedPadContact() {
        assertTrue(AceRaceArea.delayedLaunchCanFire(true, false, true, true, true, 0D));
        assertFalse(AceRaceArea.delayedLaunchCanFire(true, false, true, true, true, 0.051D));
        assertFalse(AceRaceArea.delayedLaunchCanFire(true, false, true, false, true, 0D));
        assertFalse(AceRaceArea.delayedLaunchCanFire(true, false, true, true, false, 0D));
        assertFalse(AceRaceArea.delayedLaunchCanFire(false, false, true, true, true, 0D));
        assertFalse(AceRaceArea.delayedLaunchCanFire(true, true, true, true, true, 0D));
    }
}
