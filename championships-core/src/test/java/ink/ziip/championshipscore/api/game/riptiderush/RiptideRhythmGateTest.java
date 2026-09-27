package ink.ziip.championshipscore.api.game.riptiderush;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RiptideRhythmGateTest {
    @Test void allPatternsRepeatAndOfferFullBodyPassageAtEverySpeed() {
        for (double speed : List.of(2.2,2.95,3.7,4.45,5.2,5.5)) {
            int cycle = RiptideRhythmGate.cycleTicks(speed);
            for (String variant : RiptideLevelTemplate.variants(RiptideLevelType.RHYTHM).stream()
                    .filter(v -> !v.equals("AUTO") && !RiptideRhythmGate.window(v)).toList()) {
                var masks = new HashSet<Integer>();
                int previous = -1, duration = 0;
                for (int tick=0; tick<cycle*2; tick++) {
                    int mask = 0;
                    for (int lateral=-3; lateral<=3; lateral++) {
                        boolean open = RiptideRhythmGate.opening(variant,false,tick,speed,lateral,1);
                        assertEquals(open,RiptideRhythmGate.opening(variant,false,tick+cycle,speed,lateral,1));
                        assertEquals(open,RiptideRhythmGate.opening(variant,true,tick,speed,-lateral,1));
                        if (open) mask |= 1 << (lateral+3);
                    }
                    if (mask != previous) {
                        if (previous > 0) assertTrue(duration >= 16,"At least 0.8s to clear the shutter: " + variant);
                        previous = mask; duration = 0;
                    }
                    duration++; masks.add(mask);
                }
                assertTrue(masks.size() >= 2);
                if (variant.equals("CENTER_SIDES")) assertEquals(Set.of(0b0011100,0b1100011),masks);
                if (variant.equals("ALTERNATING")) assertEquals(Set.of(0b0000111,0b1110000),masks);
                if (variant.equals("SWEEP")) assertEquals(Set.of(0b0000111,0b0011100,0b1110000),masks);
                if (variant.equals("IN_OUT")) assertEquals(Set.of(0b1111111,0b0111110,0b0011100),masks);
                if (variant.equals("CROSS_BEAT")) assertEquals(Set.of(0,0b0000111,0b1111111,0b1110000),masks);
            }
        }
    }

    @Test void windowsRepeatMirrorAndKeepTwoBlockHeadroomAndTimeToPassAtEverySpeed() {
        for (String variant : List.of("HORIZONTAL_WINDOW", "VERTICAL_WINDOW", "WINDOW_SHUTTER", "STAGGERED_WINDOWS")) {
            for (double speed : List.of(2.2, 2.95, 3.7, 4.45, 5.2, 5.5)) {
                int cycle = RiptideRhythmGate.cycleTicks(speed);
                int previous = -1, duration = 0;
                var frames = new HashSet<Integer>();
                for (int tick = 0; tick < cycle * 2; tick++) {
                    int mask = 0;
                    for (int lateral = -3; lateral <= 3; lateral++) {
                        int column = 0;
                        for (int y = 0; y <= 4; y++) {
                            boolean open = RiptideRhythmGate.opening(variant, false, tick, speed, lateral, y);
                            assertEquals(open, RiptideRhythmGate.opening(variant, true, tick, speed, -lateral, y));
                            assertEquals(open, RiptideRhythmGate.opening(variant, false, tick + cycle, speed, lateral, y));
                            if (y == 0 || y == 4) assertFalse(open);
                            else if (open) column |= 1 << (y - 1);
                        }
                        assertTrue(column == 0 || column == 3 || column == 6, "Only walk-through or one-block jump windows");
                        mask |= column << ((lateral + 3) * 3);
                    }
                    if (mask != previous) {
                        if (previous > 0) assertTrue(duration >= 16, variant);
                        previous = mask; duration = 0;
                    }
                    if (mask != 0) assertEquals(6, Integer.bitCount(mask), "Three columns, two blocks high");
                    duration++; frames.add(mask);
                }
                assertEquals(variant.equals("HORIZONTAL_WINDOW") ? 3 : 2, frames.size());
                assertEquals(variant.equals("WINDOW_SHUTTER"), frames.contains(0));
            }
        }
    }

    @Test void windowPositionsFollowHorizontalVerticalAndAlternatingPatterns() {
        double speed = 5.2; // 64-tick cycle
        for (int beat = 0; beat < 4; beat++) {
            int center = new int[]{-2, 0, 2, 0}[beat];
            for (int lateral = -3; lateral <= 3; lateral++)
                for (int y = 1; y <= 3; y++)
                    assertEquals(Math.abs(lateral - center) <= 1 && y <= 2,
                            RiptideRhythmGate.opening("HORIZONTAL_WINDOW", false, beat * 16, speed, lateral, y));
        }
        for (int tick : List.of(0, 31, 32, 63))
            for (int lateral = -3; lateral <= 3; lateral++)
                for (int y = 1; y <= 3; y++) {
                    assertEquals(Math.abs(lateral) <= 1 && (tick < 32 ? y <= 2 : y >= 2),
                            RiptideRhythmGate.opening("VERTICAL_WINDOW", false, tick, speed, lateral, y));
                    assertEquals(tick < 32 ? lateral < 0 && y <= 2 : lateral > 0 && y >= 2,
                            RiptideRhythmGate.opening("STAGGERED_WINDOWS", false, tick, speed, lateral, y));
                }
    }

    @Test void rhythmAddsNoStoppingTimeAndNewQuotasRemainPlannable() throws Exception {
        var c = RiptideTestFixtures.config();
        c.setPassCount(15); c.setMathCount(5); c.setStoppedCount(5); c.setRhythmCount(5);
        var seen = new HashSet<String>();
        for (int seed=0; seed<100; seed++) {
            var plan = RiptideCoursePlanner.plan(c,seed);
            plan.levels().stream().filter(l -> l.type() == RiptideLevelType.RHYTHM).forEach(l -> seen.add(l.variant()));
            assertEquals(5,plan.levels().stream().filter(l -> l.type()==RiptideLevelType.RHYTHM)
                    .map(RiptideCoursePlan.Level::number).distinct().count());
            assertTrue(plan.estimatedTicks()<6000);
            var without = plan.levels().stream().filter(l -> l.type()!=RiptideLevelType.RHYTHM).toList();
            assertEquals(plan.estimatedTicks(),RiptideCoursePlanner.estimateTicks(c,c.resolveGeometry(),without));
        }
        assertTrue(seen.containsAll(List.of("HORIZONTAL_WINDOW", "VERTICAL_WINDOW", "WINDOW_SHUTTER", "STAGGERED_WINDOWS")));
    }
}
