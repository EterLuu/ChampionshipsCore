package ink.ziip.championshipscore.api.game.acerace;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AceRaceRespawnPointTest {
    @Test
    void highSpeedMovementStillCapturesARespawnMarkerAlongItsPath() {
        AceRaceRespawnPoint point = new AceRaceRespawnPoint(location(0, 65, 10));

        Location from = location(0, 65, 0);
        Location to = location(0, 65, 30);
        assertTrue(point.reached(from, to));
        assertEquals(1D / 3D, point.crossingProgress(from, to), 0.000001D);
    }

    @Test
    void captureRadiusIsFourBlocks() {
        AceRaceRespawnPoint point = new AceRaceRespawnPoint(location(0, 65, 0));

        assertTrue(point.reached(location(-10, 65, 3.9), location(10, 65, 3.9)));
        assertFalse(point.reached(location(-10, 65, 4.1), location(10, 65, 4.1)));
    }

    private static Location location(double x, double y, double z) {
        return new Location(null, x, y, z);
    }
}
