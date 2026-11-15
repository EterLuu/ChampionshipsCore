package ink.ziip.championshipscore.api.game.acerace.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.api.game.acerace.mechanics.AceRaceEquipment;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

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
    void gateVectorsCannotBeChangedThroughInputsOrAccessors() {
        Vector first = new Vector(-10, 70, 10);
        Vector second = new Vector(10, 70, 10);
        var gate = new AceRaceProgressPoint(1, first, second, -32, AceRaceEquipment.NONE);
        first.setZ(100);
        second.setZ(100);
        gate.pos1().setZ(200);
        gate.pos2().setZ(200);
        assertTrue(gate.crossed(location(0, 70, 0), location(0, 70, 20)));
        assertFalse(gate.crossed(location(0, 70, 90), location(0, 70, 110)));
    }

    private static AceRaceProgressPoint point(int order, Vector pos1, Vector pos2) {
        return new AceRaceProgressPoint(order, pos1, pos2, 0, AceRaceEquipment.NONE);
    }

    private static Location location(double x, double y, double z) {
        return new Location(null, x, y, z);
    }
}
