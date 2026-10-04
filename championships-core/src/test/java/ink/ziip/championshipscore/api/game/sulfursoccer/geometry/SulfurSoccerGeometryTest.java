package ink.ziip.championshipscore.api.game.sulfursoccer.geometry;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.sulfursoccer.model.SulfurSoccerSide;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

class SulfurSoccerGeometryTest {
    private final BoundingBox right = new BoundingBox(10, 0, -2, 12, 3, 2);
    private final BoundingBox left = new BoundingBox(-12, 0, -2, -10, 3, 2);

    @Test
    void detectsBallCrossingEntireGoalBetweenTwoTicks() {
        assertEquals(
                SulfurSoccerSide.RIGHT,
                SulfurSoccerGeometry.crossedGoal(
                        right, left, new Vector(8, 1, 0), new Vector(14, 1, 0)));
        assertEquals(
                SulfurSoccerSide.LEFT,
                SulfurSoccerGeometry.crossedGoal(
                        right, left, new Vector(-8, 1, 0), new Vector(-14, 1, 0)));
    }

    @Test
    void missesAboveCrossbarBesidePostsAndAtExcludedUpperFace() {
        for (Vector offset :
                new Vector[] {new Vector(0, 4, 0), new Vector(0, 1, 3), new Vector(0, 3, 0)})
            assertNull(
                    SulfurSoccerGeometry.crossedGoal(
                            right,
                            left,
                            offset.clone().add(new Vector(8, 0, 0)),
                            offset.clone().add(new Vector(14, 0, 0))));
        assertNull(
                SulfurSoccerGeometry.crossedGoal(
                        right, left, new Vector(0, 1, 0), new Vector(0, 1, 0)));
    }

    @Test
    void choosesFirstGoalCrossedAndSupportsStationaryBallInsideGoal() {
        assertEquals(
                SulfurSoccerSide.LEFT,
                SulfurSoccerGeometry.crossedGoal(
                        right, left, new Vector(-15, 1, 0), new Vector(15, 1, 0)));
        assertEquals(
                SulfurSoccerSide.RIGHT,
                SulfurSoccerGeometry.crossedGoal(
                        right, left, new Vector(11, 1, 0), new Vector(11, 1, 0)));
    }

    @Test
    void normalizesWorldEditEndpointsAndRejectsNonFinitePositions() {
        assertEquals(
                new BoundingBox(1, 2, 3, 6, 7, 8),
                SulfurSoccerGeometry.box(new Vector(5, 6, 7), new Vector(1, 2, 3)));
        assertThrows(
                IllegalArgumentException.class, () -> SulfurSoccerGeometry.box(null, new Vector()));
        assertThrows(
                IllegalArgumentException.class,
                () -> SulfurSoccerGeometry.box(new Vector(Double.NaN, 0, 0), new Vector()));
        assertNull(
                SulfurSoccerGeometry.crossedGoal(
                        right, left, new Vector(Double.NaN, 0, 0), new Vector()));
    }

    private final BoundingBox field = new BoundingBox(-21, 0, -21, 21, 8, 21);

    @Test
    void resolvesAllFourGoalOrientationsAndPartitionsLanesFromTheKeepersView() {
        for (boolean alongX : new boolean[] {true, false}) {
            BoundingBox positive =
                    alongX
                            ? new BoundingBox(18, 0, -2, 21, 4, 3)
                            : new BoundingBox(-2, 0, 18, 3, 4, 21);
            BoundingBox negative =
                    alongX
                            ? new BoundingBox(-21, 0, -2, -18, 4, 3)
                            : new BoundingBox(-2, 0, -21, 3, 4, -18);
            for (boolean positiveGoal : new boolean[] {true, false}) {
                BoundingBox goal = positiveGoal ? positive : negative;
                var layout =
                        SulfurSoccerPenaltyLayout.resolve(
                                field, goal, positiveGoal ? negative : positive, 1);
                assertEquals(alongX, layout.alongX());
                assertEquals(positiveGoal ? 1 : -1, layout.direction());
                assertTrue(goal.contains(layout.keeper()));
                assertTrue(field.contains(layout.shooter()));
                assertFalse(goal.contains(layout.ball()));
                assertEquals(2, layout.ball().distance(layout.shooter()), 0.001);
                var blocks = new HashSet<Vector>();
                for (int slot = 0; slot < 3; slot++)
                    for (Vector point : layout.paneBlocks(slot)) assertTrue(blocks.add(point));
                assertEquals(
                        5 * 3,
                        blocks.size(),
                        "All five lanes and three vertical blocks are covered exactly once");
                double left = lateral(layout.paneBlocks(0).getFirst(), alongX);
                double right = lateral(layout.paneBlocks(2).getFirst(), alongX);
                assertEquals((alongX ? layout.direction() : -layout.direction()) > 0, left > right);
                assertThrows(IllegalArgumentException.class, () -> layout.paneBlocks(3));
            }
        }
    }

    @Test
    void placesPenaltyPanesInTheAdjacentPitchRowForBothSigns() {
        var negativeZ =
                SulfurSoccerPenaltyLayout.resolve(
                        field,
                        new BoundingBox(-4, 0, -21, 4, 4, -17),
                        new BoundingBox(-4, 0, 17, 4, 4, 21),
                        1);
        var positiveZ =
                SulfurSoccerPenaltyLayout.resolve(
                        field,
                        new BoundingBox(-4, 0, 17, 4, 4, 21),
                        new BoundingBox(-4, 0, -21, 4, 4, -17),
                        1);
        assertEquals(-16, negativeZ.paneBlocks(1).getFirst().getBlockZ());
        assertEquals(16, positiveZ.paneBlocks(1).getFirst().getBlockZ());
    }

    @Test
    void preventsApproachingTouchingOrJumpingIntoTheBallWhileAllowingLateralAim() {
        var layout =
                SulfurSoccerPenaltyLayout.resolve(
                        field,
                        new BoundingBox(18, 0, -2, 21, 4, 3),
                        new BoundingBox(-21, 0, -2, -18, 4, 3),
                        1);
        Location anchor = layout.shooter().toLocation(null);
        assertTrue(layout.allowsShooter(anchor));
        assertTrue(layout.allowsShooter(anchor.clone().add(0, 0, 1)));
        assertTrue(layout.allowsShooter(anchor.clone().add(-0.2, 0, 0)));
        assertFalse(layout.allowsShooter(anchor.clone().add(0.1, 0, 0)));
        assertFalse(layout.allowsShooter(anchor.clone().add(0, 0.1, 0)));
        assertFalse(layout.allowsShooter(anchor.clone().add(0, 0, 1.5)));
    }

    @Test
    void rejectsGoalsTooNarrowTooLowAndWithoutRoomForAnIsolatedShooter() {
        BoundingBox other = new BoundingBox(-21, 0, -2, -18, 4, 3);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SulfurSoccerPenaltyLayout.resolve(
                                field, new BoundingBox(18, 0, -1, 21, 4, 1), other, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SulfurSoccerPenaltyLayout.resolve(
                                field, new BoundingBox(18, 0, -2, 21, 2, 3), other, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SulfurSoccerPenaltyLayout.resolve(
                                new BoundingBox(15, 0, -4, 21, 5, 4),
                                new BoundingBox(18, 0, -2, 21, 4, 3),
                                other,
                                1));
    }

    private static double lateral(Vector point, boolean alongX) {
        return alongX ? point.getZ() : point.getX();
    }
}
