package ink.ziip.championshipscore.api.game.dodgebolt.mechanics;

import java.util.ArrayDeque;
import java.util.Deque;

/** Ordered shrink events. One event may intentionally remove more than one platform layer. */
public final class DodgeboltShrinkQueue {
    public static int layersForElimination(int eliminationNumber) {
        if (eliminationNumber < 1) return 0;
        return eliminationNumber <= 2 ? 2 : 1;
    }

    private final Deque<Integer> events = new ArrayDeque<>();
    private int queuedLayers;

    public int enqueue(int requestedLayers, int availableLayers) {
        int accepted = Math.min(Math.max(0, requestedLayers), Math.max(0, availableLayers));
        if (accepted == 0) return 0;
        events.addLast(accepted);
        queuedLayers += accepted;
        return accepted;
    }

    public int currentLayers() {
        return events.isEmpty() ? 0 : events.getFirst();
    }

    public int completeCurrent() {
        if (events.isEmpty()) return 0;
        int completed = events.removeFirst();
        queuedLayers -= completed;
        return completed;
    }

    public int queuedLayers() {
        return queuedLayers;
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }

    public void clear() {
        events.clear();
        queuedLayers = 0;
    }
}
