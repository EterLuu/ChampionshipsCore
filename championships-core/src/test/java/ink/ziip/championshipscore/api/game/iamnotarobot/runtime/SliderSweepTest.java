package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class SliderSweepTest {
    @Test
    void cachedReitemsAndEmptySpawnsHaveDifferentCostsAndRotateFairly() {
        for (int cost : new int[] {SliderSweep.SPAWN, SliderSweep.EQUIP}) {
            var sweep = new SliderSweep();
            int[] work = new int[64];
            for (int i = 0; i < 64; i++) {
                int card = i;
                sweep.add(
                        new SliderSweep.Card(
                                () -> true, () -> true, () -> cost, () -> ++work[card] == 128));
            }
            sweep.slice(() -> 0, 1);
            var stats = java.util.Arrays.stream(work).summaryStatistics();
            assertEquals(SliderSweep.MICROS / cost, stats.getSum());
            assertTrue(stats.getMax() - stats.getMin() <= 1);
        }
    }

    @Test
    void urgentCardsPreemptBackgroundAndYoungOrRetiredFramesNeverEquip() {
        var sweep = new SliderSweep();
        var order = new ArrayList<Integer>();
        var age = new AtomicInteger();
        var valid = new java.util.concurrent.atomic.AtomicBoolean(true);
        sweep.add(new SliderSweep.Card(() -> true, () -> false, () -> 64, () -> order.add(1)));
        sweep.add(new SliderSweep.Card(valid::get, () -> true, age::get, () -> order.add(3)));
        sweep.add(new SliderSweep.Card(() -> true, () -> true, () -> 7, () -> order.add(2)));
        sweep.slice(() -> 0, 1);
        assertEquals(List.of(2, 1), order);
        valid.set(false);
        age.set(7);
        sweep.slice(() -> 0, 1);
        assertTrue(sweep.isEmpty());
        assertEquals(List.of(2, 1), order);
    }

    @Test
    void horizontalTrackingIncludesTheFarWallAndUsesTheClientsRange() {
        assertTrue(SliderHall.inFrameRange(9.5, 28.5, 64));
        assertTrue(SliderHall.inFrameRange(26.5, 37.96875 + 64, 64));
        assertFalse(SliderHall.inFrameRange(26.5, 37.96875 + 64.01, 64));
        assertFalse(SliderHall.inFrameRange(55, 65, 16));
        assertFalse(SliderHall.inFrameRange(256, 28.5, 64));
    }
}
