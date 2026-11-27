package ink.ziip.championshipscore.api.game.craftingfeast.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Random;

class FeastGazeTest {
    @Test
    void theBodyTurnsAllTheWayToItsRestingDirection() {
        var gaze = new FeastGaze(90);
        gaze.rest(-90);
        for (int i = 0; i < 10; i++) gaze.update(5, Float.NaN, 0, false, new Random(1));
        assertEquals(0, FeastGaze.wrap(gaze.body() + 90), 1e-3, "No partial, sideways stop");
    }

    @Test
    void theHeadGlancesAroundButStaysWithinItsNeckRange() {
        var gaze = new FeastGaze(0);
        var random = new Random(7);
        boolean turned = false;
        for (int i = 0; i < 400; i++) {
            gaze.update(5, Float.NaN, 0, false, random);
            float offset = FeastGaze.wrap(gaze.head() - gaze.body());
            assertTrue(Math.abs(offset) <= FeastGaze.MAX_HEAD + 1e-3, "offset " + offset);
            assertTrue(gaze.pitch() >= -30 && gaze.pitch() <= 30);
            turned |= Math.abs(offset) > 10;
        }
        assertTrue(turned, "Idle guests look around instead of staring straight ahead");
    }

    @Test
    void aNearbyCookDrawsTheEyesButNotBehindTheShoulder() {
        var gaze = new FeastGaze(0);
        for (int i = 0; i < 10; i++) gaze.update(5, 40, 10, false, new Random(3));
        assertEquals(40, gaze.head(), 1e-3);
        assertEquals(10, gaze.pitch(), 1e-3);
        assertEquals(0, gaze.body(), 1e-3, "Only the head turns");

        var behind = new FeastGaze(0);
        for (int i = 0; i < 10; i++) behind.update(5, 170, 0, false, new Random(3));
        assertTrue(Math.abs(FeastGaze.wrap(behind.head() - 170)) > 60);
    }
}
