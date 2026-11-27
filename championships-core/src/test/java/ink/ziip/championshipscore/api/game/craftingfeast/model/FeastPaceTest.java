package ink.ziip.championshipscore.api.game.craftingfeast.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeastPaceTest {
    /** The bundled pace: 80 % at opening, 150 % for the last two minutes of ten. */
    private final FeastPace pace = new FeastPace(12, 80, 150, 120, 600);

    @Test
    void climbsInTenPercentStepsFromTheOpeningRateToThePeak() {
        assertEquals(80, pace.percent(0));
        assertEquals(80, pace.percent(68));
        assertEquals(90, pace.percent(69));
        assertEquals(140, pace.percent(479));
        assertEquals(150, pace.percent(480));
        assertEquals(150, pace.percent(599));
        int previous = pace.percent(0);
        for (int second = 1; second < 600; second++) {
            int percent = pace.percent(second);
            assertTrue(percent == previous || percent == previous + FeastPace.STEP, "s" + second);
            previous = percent;
        }
    }

    @Test
    void peakCoversOnlyTheLastPeakSeconds() {
        assertEquals(480, pace.peakStart());
        assertFalse(pace.peak(479));
        assertTrue(pace.peak(480));
        assertTrue(pace.peak(599));
    }

    @Test
    void aPeakNoHigherThanTheOpeningKeepsAFlatFlow() {
        FeastPace flat = new FeastPace(12, 100, 100, 120, 600);
        assertEquals(100, flat.percent(0));
        assertEquals(100, flat.percent(599));
        assertFalse(flat.peak(599));
        assertFalse(new FeastPace(12, 80, 150, 0, 600).peak(599), "no peak stretch");
    }

    @Test
    void aPeakLongerThanTheServiceStartsAtOnce() {
        FeastPace whole = new FeastPace(12, 80, 150, 120, 60);
        assertEquals(150, whole.percent(0));
        assertTrue(whole.peak(0));
    }

    @Test
    void intervalShrinksWithTheFlow() {
        assertEquals(15, pace.interval(0));
        assertEquals(13, pace.interval(69));
        assertEquals(8, pace.interval(480));
        assertEquals(3, new FeastPace(5, 400, 400, 0, 600).interval(600));
    }
}
