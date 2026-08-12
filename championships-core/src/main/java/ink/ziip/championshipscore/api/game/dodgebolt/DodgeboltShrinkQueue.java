package ink.ziip.championshipscore.api.game.dodgebolt;

import java.util.ArrayDeque;
import java.util.Deque;

/** Ordered shrink events. One event may intentionally remove more than one platform layer. */
final class DodgeboltShrinkQueue {
    private final Deque<Integer> events = new ArrayDeque<>();
    private int queuedLayers;

    int enqueue(int requestedLayers, int availableLayers) {
        int accepted = Math.min(Math.max(0, requestedLayers), Math.max(0, availableLayers));
        if (accepted == 0) return 0;
        events.addLast(accepted);
        queuedLayers += accepted;
        return accepted;
    }

    int currentLayers() {
        return events.isEmpty() ? 0 : events.getFirst();
    }

    int completeCurrent() {
        if (events.isEmpty()) return 0;
        int completed = events.removeFirst();
        queuedLayers -= completed;
        return completed;
    }

    int queuedLayers() {
        return queuedLayers;
    }

    boolean isEmpty() {
        return events.isEmpty();
    }

    void clear() {
        events.clear();
        queuedLayers = 0;
    }
}
