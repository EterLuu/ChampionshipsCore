package ink.ziip.championshipscore.api.game.acerace;

import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AceRaceRulesTest {
    @Test
    void parsesSegmentEquipment() {
        assertEquals(AceRaceEquipment.DOLPHINS_GRACE,
                AceRaceEquipment.fromConfig("dolphins_grace"));
        assertEquals("海豚的恩惠", AceRaceEquipment.DOLPHINS_GRACE.displayName());
    }

    @Test
    void endsOnlyAfterEveryCurrentParticipantHasFinished() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertFalse(AceRaceArea.allParticipantsFinished(List.of(first, second), List.of(first)));
        assertTrue(AceRaceArea.allParticipantsFinished(List.of(first, second), List.of(first, second)));
    }

    @Test
    void removedOrDuplicateRosterEntriesDoNotPreventCompletion() {
        UUID current = UUID.randomUUID();
        UUID departed = UUID.randomUUID();

        assertTrue(AceRaceArea.allParticipantsFinished(
                List.of(current, current), List.of(departed, current)));
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
    void clampsDownwardAimToUsefulMinimum() {
        assertEquals(0.9D, AceRaceArea.calculateAimedVerticalVelocity(1D, -1D));
    }

    @Test
    void redPadDownwardAimUsesNinetyPercentMinimum() {
        assertEquals(0.675D, AceRaceArea.calculateAimedVerticalVelocity(
                AceRaceArea.launchBaseVerticalVelocity(Material.RED_WOOL), -1D));
    }

    @Test
    void preservesBaseLiftAtLevelAim() {
        assertEquals(1D, AceRaceArea.calculateAimedVerticalVelocity(1D, 0D));
    }

    @Test
    void clampsUpwardAimToMaximum() {
        assertEquals(1.2D, AceRaceArea.calculateAimedVerticalVelocity(1D, 1D));
    }

    @Test
    void retainsPitchScalingBetweenBounds() {
        assertEquals(1.1D, AceRaceArea.calculateAimedVerticalVelocity(1D, 0.1D));
    }

    @Test
    void formatsClocksWithTwoDigitMinutesAndSeconds() {
        assertEquals("00:00", Utils.formatMinutesSeconds(0));
        assertEquals("00:07", Utils.formatMinutesSeconds(7));
        assertEquals("12:23", Utils.formatMinutesSeconds(12 * 60L + 23));
    }

    /**
     * Replays a client's horizontal speed after a pad impulse. The only difference between an honest and an
     * exploited launch is the drag paid on the launch tick: {@code 0.6 * 0.91} while still standing on the
     * wool versus {@code 0.91} once the racer jumped clear of it. Both racers hold forward, so both carry
     * the same sprint air acceleration.
     */
    private static double branchSpeed(double horizontalVelocity, int ticks, double launchTickDrag,
                                      double airAcceleration) {
        double speed = horizontalVelocity;
        for (int tick = 1; tick <= ticks; tick++) {
            speed = speed * (tick == 1 ? launchTickDrag : 0.91D) + airAcceleration;
        }
        return speed;
    }

    private static double groundedBranchSpeed(double horizontalVelocity, int ticks) {
        return branchSpeed(horizontalVelocity, ticks, 0.6D * 0.91D, 0.026D);
    }

    /** Same launch, but applied after the racer left the ground: the branch this fix exists to remove. */
    private static double airborneBranchSpeed(double horizontalVelocity, int ticks) {
        return branchSpeed(horizontalVelocity, ticks, 0.91D, 0.026D);
    }

    /** An honest racer who never touches the movement keys, the slowest legitimate flight. */
    private static double coastingBranchSpeed(double horizontalVelocity, int ticks) {
        return branchSpeed(horizontalVelocity, ticks, 0.6D * 0.91D, 0D);
    }

    @Test
    void launchTickItselfTravelsTheRawImpulse() {
        assertEquals(2D, AceRaceArea.launchEnvelopeSpeed(2D, 0));
        assertEquals(2D, AceRaceArea.launchEnvelopeDistance(2D, 0));
    }

    @Test
    void envelopeMatchesTheGroundedBranchOfASprintingRacer() {
        // 2.0 * 0.546 + 0.026
        assertEquals(1.118D, AceRaceArea.launchEnvelopeSpeed(2D, 1), 1e-9D);
        for (int tick = 0; tick <= 20; tick++) {
            assertEquals(groundedBranchSpeed(2D, tick), AceRaceArea.launchEnvelopeSpeed(2D, tick), 1e-9D);
        }
    }

    @Test
    void envelopeStaysBelowTheAirborneBranchForBothPads() {
        for (Material pad : List.of(Material.RED_WOOL, Material.ORANGE_WOOL)) {
            double horizontal = AceRaceArea.launchHorizontalVelocity(pad);
            for (int tick = 1; tick <= 20; tick++) {
                assertTrue(AceRaceArea.launchEnvelopeSpeed(horizontal, tick)
                                < airborneBranchSpeed(horizontal, tick),
                        pad + " jump exploit must be detectable at tick " + tick);
            }
        }
    }

    @Test
    void jumpExploitExceedsTheDistanceBudgetWithinThreeTicks() {
        double horizontal = AceRaceArea.launchHorizontalVelocity(Material.RED_WOOL);
        double exploited = horizontal;
        double budget = AceRaceArea.launchEnvelopeDistance(horizontal, 3);
        for (int tick = 1; tick <= 3; tick++) exploited += airborneBranchSpeed(horizontal, tick);
        // The 0.5 block tolerance that absorbs movement-packet jitter must not hide the exploit.
        assertTrue(exploited - budget > 0.5D,
                "exploited " + exploited + " should clear budget " + budget + " plus tolerance");
    }

    @Test
    void honestFlightNeverExceedsTheDistanceBudget() {
        for (Material pad : List.of(Material.RED_WOOL, Material.ORANGE_WOOL)) {
            double horizontal = AceRaceArea.launchHorizontalVelocity(pad);
            double sprinting = horizontal;
            double coasting = horizontal;
            for (int tick = 1; tick <= 20; tick++) {
                sprinting += groundedBranchSpeed(horizontal, tick);
                coasting += coastingBranchSpeed(horizontal, tick);
                double budget = AceRaceArea.launchEnvelopeDistance(horizontal, tick);
                assertTrue(sprinting <= budget,
                        pad + " sprinting racer must never be corrected at tick " + tick);
                assertTrue(coasting <= budget,
                        pad + " coasting racer must never be corrected at tick " + tick);
            }
        }
    }

    @Test
    void envelopeDistanceGrowsMonotonically() {
        double previous = AceRaceArea.launchEnvelopeDistance(2D, 0);
        for (int tick = 1; tick <= 20; tick++) {
            double current = AceRaceArea.launchEnvelopeDistance(2D, tick);
            assertTrue(current > previous, "distance must grow at tick " + tick);
            previous = current;
        }
    }

    @Test
    void debtFreeFlightKeepsTheEnvelopeSpeed() {
        assertEquals(1.5D, AceRaceArea.enforcedLaunchSpeed(1.5D, 0D));
        assertEquals(1.5D, AceRaceArea.enforcedLaunchSpeed(1.5D, -3D));
    }

    @Test
    void debtIsRepaidBySlowingBelowTheEnvelope() {
        // Six repayment ticks, so a three block excess costs half a block of speed each tick.
        assertEquals(1D, AceRaceArea.enforcedLaunchSpeed(1.5D, 3D), 1e-9D);
        assertTrue(AceRaceArea.enforcedLaunchSpeed(1.5D, 0.6D) < 1.5D);
    }

    @Test
    void repaymentNeverDrivesTheRacerBackwards() {
        assertEquals(0D, AceRaceArea.enforcedLaunchSpeed(0.4D, 100D));
    }
}
