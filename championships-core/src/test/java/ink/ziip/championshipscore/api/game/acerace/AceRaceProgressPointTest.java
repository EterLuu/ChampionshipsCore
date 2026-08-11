package ink.ziip.championshipscore.api.game.acerace;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AceRaceProgressPointTest {

    @Test
    void shortGateExpandsToTwentyBlocksAroundItsCentre() {
        AceRaceProgressPoint point = point(1, new Vector(0, 64, 0), new Vector(7, 64, 0));

        assertTrue(point.crossed(location(-5, 65, -2), location(-5, 65, 2)));
        assertFalse(point.crossed(location(-7, 65, -2), location(-7, 65, 2)));
        assertEquals(-6, point.pos1().getBlockX());
        assertEquals(13, point.pos2().getBlockX());
    }

    @Test
    void progressGateIncludesThreeBlocksBelowSelectedLine() {
        AceRaceProgressPoint point = point(1, new Vector(-10, 64, 0), new Vector(10, 64, 0));

        assertTrue(point.crossed(location(0, 62, -2), location(0, 62, 2)));
        assertFalse(point.crossed(location(0, 61, -2), location(0, 61, 2)));
    }

    @Test
    void oneFastTrajectoryCanCrossSeveralConsecutiveGates() {
        AceRaceProgressPoint first = point(1, new Vector(-10, 64, 10), new Vector(10, 64, 10));
        AceRaceProgressPoint second = point(2, new Vector(-10, 64, 20), new Vector(10, 64, 20));

        Location from = location(0, 70, 0);
        Location to = location(0, 70, 30);
        assertTrue(first.crossed(from, to));
        assertTrue(second.crossed(from, to));
    }

    @Test
    void respawnMarkersBindToPrecedingGateWithoutWrappingStartSegment() {
        List<AceRaceProgressPoint> progressPoints = List.of(
                point(1, new Vector(-10, 64, 10), new Vector(10, 64, 10)),
                point(2, new Vector(-10, 64, 20), new Vector(10, 64, 20)));
        List<AceRaceRespawnPoint> respawnPoints = List.of(
                new AceRaceRespawnPoint(location(0, 65, 0)),
                new AceRaceRespawnPoint(location(0, 65, 12)),
                new AceRaceRespawnPoint(location(0, 65, 18)));

        assertEquals(List.of(-1, 0, 1), AceRaceArea.bindRespawnPoints(
                progressPoints, respawnPoints, location(0, 65, 0), location(0, 65, 22)));
    }

    @Test
    void respawnBindingUsesCoordinatesWhenMarkersAreConfiguredOutOfOrder() {
        List<AceRaceProgressPoint> progressPoints = List.of(
                point(1, new Vector(-10, 64, 10), new Vector(10, 64, 10)),
                point(2, new Vector(-10, 64, 20), new Vector(10, 64, 20)));
        List<AceRaceRespawnPoint> respawnPoints = List.of(
                new AceRaceRespawnPoint(location(0, 65, 22)),
                new AceRaceRespawnPoint(location(0, 65, 0)),
                new AceRaceRespawnPoint(location(0, 65, 12)));

        assertEquals(List.of(1, -1, 0), AceRaceArea.bindRespawnPoints(
                progressPoints, respawnPoints, location(0, 65, 0), location(0, 65, 22)));
    }

    @Test
    void finalGateUsesNearestPreFinishMarkerWhenNoMarkerIsAfterIt() {
        List<AceRaceProgressPoint> progressPoints = List.of(
                point(1, new Vector(-10, 64, 10), new Vector(10, 64, 10)),
                point(2, new Vector(-10, 64, 40), new Vector(10, 64, 40)));
        List<AceRaceRespawnPoint> respawnPoints = List.of(
                new AceRaceRespawnPoint(location(0, 65, 0)),
                new AceRaceRespawnPoint(location(0, 65, 12)),
                new AceRaceRespawnPoint(location(0, 65, 25)));

        assertEquals(List.of(-1, 0, 1), AceRaceArea.bindRespawnPoints(
                progressPoints, respawnPoints, location(0, 65, 0), location(0, 65, 42)));
    }

    private static AceRaceProgressPoint point(int order, Vector pos1, Vector pos2) {
        return new AceRaceProgressPoint(order, pos1, pos2, 0, AceRaceEquipment.NONE);
    }

    private static Location location(double x, double y, double z) {
        return new Location(null, x, y, z);
    }
}
