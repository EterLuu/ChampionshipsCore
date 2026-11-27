package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.iamnotarobot.model.SliderMapIds;

import org.junit.jupiter.api.Test;

import java.util.*;

class SliderDeliveryTest {
    private final SliderBandwidth bandwidth = new SliderBandwidth();

    @Test
    void bothRoundsReachEveryConnectionInOrderAtTheGlobalAndIndividualCaps() {
        var queue = new SliderDelivery();
        var seen = new LinkedHashMap<UUID, BitSet>();
        for (int i = 0; i < 84; i++) {
            var id = new UUID(0, i);
            queue.enter(id);
            seen.put(id, new BitSet());
        }
        int[] first = {0};
        for (int tick = 0; tick < 600; tick++) {
            var perTick = new HashMap<UUID, Integer>();
            int count =
                    queue.slice(
                            tick * 50_000_000L,
                            bandwidth,
                            (id, slot) -> {
                                var s = seen.get(id);
                                assertFalse(s.get(slot));
                                if ((slot / SliderMapIds.TILES) % 2 == 0) first[0]++;
                                else assertEquals(84 * 512, first[0]);
                                s.set(slot);
                                assertTrue(
                                        perTick.merge(id, 1, Integer::sum)
                                                <= SliderDelivery.VIEWER_LIMIT);
                                return true;
                            });
            assertTrue(count <= SliderDelivery.TICK_LIMIT);
            if (tick == 0) assertEquals(84, perTick.size());
        }
        assertTrue(seen.values().stream().allMatch(s -> s.cardinality() == 1024));
    }

    @Test
    void urgentVisibleTilesPreemptBackgroundAndRangeChangesKeepTheCache() {
        var queue = new SliderDelivery();
        var id = UUID.randomUUID();
        queue.enter(id);
        var visible = new BitSet();
        visible.set(255);
        var sent = new ArrayList<Integer>();
        queue.prioritize(id, visible);
        queue.slice(0, bandwidth, (k, n) -> sent.add(n));
        assertEquals(255, sent.getFirst());
        queue.enter(id);
        queue.prioritize(id, visible);
        queue.slice(50_000_000L, bandwidth, (k, n) -> sent.add(n));
        assertEquals(1, Collections.frequency(sent, 255));
        queue.forget(id);
        assertEquals(0, queue.slice(100_000_000L, bandwidth, (k, n) -> fail()));
        queue.enter(id);
        queue.prioritize(id, visible);
        queue.slice(150_000_000L, bandwidth, (k, n) -> sent.add(n));
        assertEquals(2, Collections.frequency(sent, 255));
    }

    @Test
    void realSecondLimitAlsoCoversCatchUpTicksAndRetriesFailedSends() {
        var queue = new SliderDelivery();
        for (int i = 0; i < 84; i++) queue.enter(new UUID(0, i));
        assertEquals(0, queue.slice(0, bandwidth, (id, n) -> false));
        int total = 0;
        for (int i = 0; i < 40; i++) total += queue.slice(1, bandwidth, (id, n) -> true);
        assertEquals(SliderDelivery.SECOND_LIMIT, total);
        assertEquals(0, queue.slice(1_000_000_000L, bandwidth, (id, n) -> true));
        assertEquals(
                SliderDelivery.TICK_LIMIT, queue.slice(1_000_000_001L, bandwidth, (id, n) -> true));
    }

    @Test
    void chunkTrafficConsumesBandwidthAndQuietTicksRecoverIt() {
        bandwidth.observe(0, 18_000_000);
        assertEquals(0, bandwidth.allowed(0));
        bandwidth.observe(250_000_000, 0);
        assertEquals(0, bandwidth.allowed(2200));
        assertTrue(bandwidth.allowed(0) >= SliderDelivery.TICK_LIMIT);
        assertTrue(bandwidth.allowed(2050) < SliderDelivery.TICK_LIMIT);
        bandwidth.observe(1_000_000_000, 0);
        assertTrue(bandwidth.allowed(2050) >= SliderDelivery.TICK_LIMIT);
    }

    @Test
    void aViewerAtTheTrackingEdgePrioritizesOnlyReachableColumnsOfEitherRound() {
        var visible = new BitSet();
        visible.set(0, SliderMapIds.COUNT);
        var tracked = new BitSet();
        SliderAudience.selectTracked(visible, tracked, -71.5, 20 + 1 / 32.0, 64);
        assertEquals(16, tracked.cardinality());
        for (int row = 0; row < 8; row++) {
            assertTrue(tracked.get(row * 16));
            assertTrue(tracked.get(128 + row * 16));
        }
        SliderAudience.selectTracked(visible, tracked, 9, 28, 64);
        assertEquals(visible, tracked);
    }

    @Test
    void temporarilyUnavailableRecipientDoesNotHoldTheOtherClientsSecondRound() {
        var q = new SliderDelivery();
        var online = UUID.randomUUID();
        var absent = UUID.randomUUID();
        q.enter(online);
        q.enter(absent);
        var received = new BitSet();
        for (int tick = 0; tick < 60; tick++)
            q.slice(
                    tick * 50_000_000L,
                    bandwidth,
                    (id, slot) -> {
                        if (id.equals(absent)) return false;
                        received.set(slot);
                        return true;
                    });
        assertEquals(1024, received.cardinality());
    }
}
