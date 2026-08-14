package ink.ziip.championshipscore.api.game.buildmart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildMartWindVentPolicyTest {
    @Test
    void reachesAbsoluteY200AtTheFasterVelocity() {
        assertEquals(3.0, BuildMartWindVentPolicy.upwardVelocity(100.0));
        assertEquals(3.0, BuildMartWindVentPolicy.upwardVelocity(197.0));
        assertEquals(1.0, BuildMartWindVentPolicy.upwardVelocity(199.0));
        assertEquals(0.0, BuildMartWindVentPolicy.upwardVelocity(200.0));
        assertEquals(0.0, BuildMartWindVentPolicy.upwardVelocity(201.0));
    }

    @Test
    void glidingPlayersAreNeverAffected() {
        assertTrue(BuildMartWindVentPolicy.affectsPlayer(false, true));
        assertFalse(BuildMartWindVentPolicy.affectsPlayer(true, true));
        assertFalse(BuildMartWindVentPolicy.affectsPlayer(false, false));
    }
}
