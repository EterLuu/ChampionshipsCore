package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import ink.ziip.championshipscore.api.game.iamnotarobot.model.SliderMapIds;

import java.util.*;
import java.util.function.BiPredicate;

/** One immutable edition. Chunk tracking changes priorities, never ownership of cached pixels. */
final class SliderDelivery {
    static final int TICK_LIMIT = 159, SECOND_LIMIT = 3180, VIEWER_LIMIT = 20;
    private static final BitSet FIRST = new BitSet(), BOTH = new BitSet();

    static {
        BOTH.set(0, SliderMapIds.COUNT);
        for (int slot = 0; slot < SliderMapIds.COUNT; slot += 2 * SliderMapIds.TILES)
            FIRST.set(slot, slot + SliderMapIds.TILES);
    }

    private static final class Session {
        final BitSet missing = (BitSet) BOTH.clone(), urgent = new BitSet();
        int sent;
        boolean unavailable;
        int firstLeft = FIRST.cardinality();
    }

    private final Map<UUID, Session> sessions = new LinkedHashMap<>();
    private final ArrayDeque<UUID> rotation = new ArrayDeque<>();

    private record Batch(long time, int count) {}

    private final ArrayDeque<Batch> recent = new ArrayDeque<>();
    private int outstanding;

    void enter(UUID id) {
        if (!sessions.containsKey(id)) {
            sessions.put(id, new Session());
            rotation.addLast(id);
        }
    }

    void forget(UUID id) {
        if (sessions.remove(id) != null) rotation.remove(id);
    }

    void prioritize(UUID id, BitSet visible) {
        var s = sessions.get(id);
        if (s != null && !s.missing.isEmpty()) {
            s.urgent.or(visible);
            s.urgent.and(s.missing);
        }
    }

    boolean needsPixels(UUID id) {
        var session = sessions.get(id);
        return session != null && !session.missing.isEmpty();
    }

    int slice(long now, SliderBandwidth bandwidth, BiPredicate<UUID, Integer> send) {
        while (!recent.isEmpty() && now - recent.getFirst().time >= 1_000_000_000L)
            outstanding -= recent.removeFirst().count;
        int limit =
                Math.min(
                        bandwidth.allowed(outstanding),
                        Math.min(TICK_LIMIT, SECOND_LIMIT - outstanding));
        int count = 0;
        for (int priority = 0; priority < 3 && count < limit; priority++) {
            int skipped = 0;
            while (skipped < rotation.size() && count < limit) {
                UUID id = rotation.removeFirst();
                rotation.addLast(id);
                var s = sessions.get(id);
                int slot = -1;
                if (s.sent < VIEWER_LIMIT) {
                    if (priority == 0) slot = s.urgent.nextSetBit(0);
                    else if (priority == 1 && s.firstLeft > 0) slot = firstMissing(s.missing);
                    else if (priority == 2 && s.firstLeft == 0) slot = s.missing.nextSetBit(0);
                }
                if (slot < 0) {
                    skipped++;
                    continue;
                }
                if (!send.test(id, slot)) {
                    s.sent = VIEWER_LIMIT;
                    s.unavailable = true;
                    skipped++;
                    continue;
                }
                s.missing.clear(slot);
                s.urgent.clear(slot);
                if (FIRST.get(slot)) s.firstLeft--;
                count++;
                s.sent++;
                skipped = 0;
            }
            // A temporarily capped first-round recipient must not be overtaken by round two.
            if (priority == 1
                    && sessions.values().stream().anyMatch(s -> s.firstLeft > 0 && !s.unavailable))
                break;
        }
        if (count > 0) {
            recent.addLast(new Batch(now, count));
            outstanding += count;
        }
        for (var s : sessions.values()) {
            s.sent = 0;
            s.unavailable = false;
            s.urgent.clear();
        }
        return count;
    }

    private static int firstMissing(BitSet missing) {
        int slot = missing.nextSetBit(0);
        while (slot >= 0 && !FIRST.get(slot))
            slot = missing.nextSetBit((slot / SliderMapIds.TILES + 1) * SliderMapIds.TILES);
        return slot;
    }
}
