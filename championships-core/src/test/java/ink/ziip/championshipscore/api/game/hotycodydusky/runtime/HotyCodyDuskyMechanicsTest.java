package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class HotyCodyDuskyMechanicsTest {
    private static Vector3f applyTransform(Transformation t, Vector3f local) {
        return new Vector3f(local)
                .mul(t.getScale())
                .rotate(t.getLeftRotation())
                .add(t.getTranslation());
    }

    @Test
    void hurtAnimationYawMatchesVanillaDirectionalTiltConvention() {
        // Victim facing South (yaw=0, +Z); attacker directly in front (+Z) -> relative yaw 0.
        assertEquals(0f, HotyCodyDuskyTeamArea.hurtAnimationYaw(0, 2, 0, 0, 0f), 1e-4f);
        // Attacker to the victim's right (-X when facing +Z) -> relative yaw +90.
        assertEquals(90f, HotyCodyDuskyTeamArea.hurtAnimationYaw(-2, 0, 0, 0, 0f), 1e-4f);
        // Attacker behind victim (-Z when facing +Z) -> relative yaw -180.
        assertEquals(-180f, HotyCodyDuskyTeamArea.hurtAnimationYaw(0, -2, 0, 0, 0f), 1e-4f);
        // Victim facing West (yaw=90, -X); attacker directly in front (-X) -> relative yaw 0.
        assertEquals(0f, HotyCodyDuskyTeamArea.hurtAnimationYaw(-2, 0, 0, 0, 90f), 1e-4f);
    }

    @Test
    void redesignedRopeSegmentsConnectEndToEndWithRigidSlerpTransforms() {
        float segLen = 1.05f;
        float[] angles = {-0.20f, -0.35f, -0.50f, -0.62f};
        PurpleRope.RopeFrame frame = PurpleRope.computeFrame(segLen, angles);

        assertEquals(PurpleRope.SEGMENT_COUNT + 1, frame.joints().length);
        assertEquals(0f, frame.joints()[0].x, 1e-5f);
        assertEquals(-0.06f, frame.joints()[0].y, 1e-5f);

        for (int i = 0; i < PurpleRope.SEGMENT_COUNT; i++) {
            // Top center (0.5, 1.0, 0.5) of cord i must sit exactly at joint[i].
            Vector3f cordTop = applyTransform(frame.cords()[i], new Vector3f(0.5f, 1.0f, 0.5f));
            assertEquals(frame.joints()[i].x, cordTop.x, 1e-4f);
            assertEquals(frame.joints()[i].y, cordTop.y, 1e-4f);
            assertEquals(frame.joints()[i].z, cordTop.z, 1e-4f);

            // Joint distance must equal exact segment length (zero stretching).
            assertEquals(segLen, frame.joints()[i].distance(frame.joints()[i + 1]), 1e-4f);

            // Knot center (0.5, 0.5, 0.5) must sit exactly on joint[i + 1].
            Vector3f knotCenter = applyTransform(frame.knots()[i], new Vector3f(0.5f, 0.5f, 0.5f));
            assertEquals(frame.joints()[i + 1].x, knotCenter.x, 1e-4f);
            assertEquals(frame.joints()[i + 1].y, knotCenter.y, 1e-4f);
            assertEquals(frame.joints()[i + 1].z, knotCenter.z, 1e-4f);

            // Left rotation must remain a unit quaternion for distortion-free client SLERP.
            assertEquals(1f, frame.cords()[i].getLeftRotation().lengthSquared(), 1e-5f);
        }

        // Both stirrup straps must originate from the bottom knot joint.
        Vector3f bottomJoint = frame.joints()[PurpleRope.SEGMENT_COUNT];
        Vector3f leftTop = applyTransform(frame.leftStrap(), new Vector3f(0.5f, 1.0f, 0.5f));
        Vector3f rightTop = applyTransform(frame.rightStrap(), new Vector3f(0.5f, 1.0f, 0.5f));
        assertEquals(bottomJoint.x, leftTop.x, 1e-4f);
        assertEquals(bottomJoint.y, leftTop.y, 1e-4f);
        assertEquals(bottomJoint.x, rightTop.x, 1e-4f);
        assertEquals(bottomJoint.y, rightTop.y, 1e-4f);

        // Wooden rescue handle center must sit HANDLE_DROP away from the bottom knot along
        // endAngle.
        Vector3f barCenter = applyTransform(frame.handleBar(), new Vector3f(0.5f, 0.5f, 0.5f));
        assertEquals(PurpleRope.HANDLE_DROP, bottomJoint.distance(barCenter), 1e-4f);
    }

    @Test
    void ropeClicksAlternateRightThenLeftAcrossFivePulls() {
        assertTrue(PurpleRope.matchesAlternateClick(0, true));
        assertFalse(PurpleRope.matchesAlternateClick(0, false));
        assertTrue(PurpleRope.matchesAlternateClick(1, false));
        assertFalse(PurpleRope.matchesAlternateClick(1, true));
        assertTrue(PurpleRope.matchesAlternateClick(4, true));
        assertEquals(5, PurpleRope.CLICKS_REQUIRED);
    }

    @Test
    void meteorSpeedAcceleratesSmoothlyAndCapsAtMax() {
        double speed = PurpleCorruption.METEOR_INITIAL_SPEED;
        assertEquals(0.55, speed, 1e-6);
        speed = PurpleCorruption.nextMeteorSpeed(speed);
        assertEquals(0.61, speed, 1e-6);
        for (int i = 0; i < 60; i++) speed = PurpleCorruption.nextMeteorSpeed(speed);
        assertEquals(PurpleCorruption.METEOR_MAX_SPEED, speed, 1e-6);
        assertEquals(6, PurpleCorruption.MAX_ACTIVE_METEORS);
    }

    @Test
    void liftHeightStepsByIntegerBlocksPerTwoTicksAndClampsBounds() {
        assertEquals(1.0, PurpleLift.nextHeight(0.0, 1), 1e-6);
        assertEquals(PurpleLift.MAX_HEIGHT, PurpleLift.nextHeight(56.5, 1), 1e-6);
        assertEquals(55.0, PurpleLift.nextHeight(57.0, -1), 1e-6);
        assertEquals(0.0, PurpleLift.nextHeight(1.0, -1), 1e-6);
        assertEquals(2, PurpleLift.STEP_CADENCE);
    }
}
