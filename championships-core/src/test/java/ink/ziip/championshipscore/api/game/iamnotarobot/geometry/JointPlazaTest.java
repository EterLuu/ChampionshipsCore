package ink.ziip.championshipscore.api.game.iamnotarobot.geometry;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class JointPlazaTest {
    @Test
    void boundsAndSweepUseHalfOpenIntervals() {
        assertTrue(JointPlaza.contains(7.5, 1, 39.5, false));
        assertTrue(JointPlaza.contains(18.5, 12, 70.5, false));
        assertTrue(JointPlaza.contains(-5.5, 12, 70.5, false));
        assertTrue(JointPlaza.contains(11.5, 9, 60.5, false));
        assertFalse(JointPlaza.contains(9, 1, 82.5, false));
        assertTrue(JointPlaza.contains(9, 1, 82.5, true));
        assertTrue(JointPlaza.intersectsSweep(12, 2, 60, 12.6, 3.8, 60.6));
        assertFalse(JointPlaza.intersectsSweep(33, 1, 60, 33.6, 2.8, 60.6));
        assertEquals(new Vector(-1.5, 1, 57.5), JointPlaza.ejectTarget(-5));
        assertEquals(new Vector(20.5, 1, 57.5), JointPlaza.ejectTarget(25));
        assertEquals(new Vector(9.5, 1, 57.5), JointPlaza.ejectTarget(Double.NaN));
        assertEquals(new Vector(9.5, 1, 57.5), JointPlaza.ejectTarget(Double.POSITIVE_INFINITY));
        assertEquals(new Vector(10.5, 1, 41.5), JointPlaza.restoreSpawn(3));
        assertFalse(JointPlaza.intersectsSweep(7, 0, 59, 8, 1, 60));
        assertTrue(JointPlaza.intersectsSweep(7, 11, 60, 8, 12, 61));
        assertTrue(JointPlaza.intersectsSweep(13, 9, 63, 13.6, 10.8, 63.6));
        assertFalse(JointPlaza.intersectsSweep(28, 9, 63, 28.6, 10.8, 63.6));
        assertFalse(JointPlaza.intersectsSweep(7, 2, 67, 8, 4, 68));
        assertTrue(JointPlaza.contains(1.5, 2, 60.5, false)); // can enter after excavation
        assertTrue(JointPlaza.contains(1.5, 1, 60.5, false)); // stand in a fully excavated column
        assertFalse(JointPlaza.inDigZone(1, 1, 60)); // floor clearance stays uneditable
        assertFalse(JointPlaza.contains(19.5, 2, 60.5, false)); // white fixed base
        assertTrue(JointPlaza.chunkInBounds(-3, 2));
        assertTrue(JointPlaza.chunkInBounds(3, 6));
        assertFalse(JointPlaza.chunkInBounds(-4, 2));
        assertFalse(JointPlaza.chunkInBounds(4, 6));
        assertEquals(35, JointPlaza.preloadPoints().size());
    }
}
