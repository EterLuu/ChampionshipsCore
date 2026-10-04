package ink.ziip.championshipscore.api.game.frostbite.mechanics;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps supply descriptions readable across HUD refreshes, including both icicle rewards. */
public final class FrostbiteSupplyHints {
    private record Hint(String text, int until) {}

    private final Map<UUID, ArrayDeque<Hint>> pending = new HashMap<>();

    public void add(UUID player, String text, int tick) {
        current(player, tick);
        var queue = pending.computeIfAbsent(player, ignored -> new ArrayDeque<>());
        queue.addLast(new Hint(text, (queue.isEmpty() ? tick : queue.getLast().until()) + 100));
    }

    public String current(UUID player, int tick) {
        var queue = pending.get(player);
        if (queue == null) return null;
        while (!queue.isEmpty() && queue.getFirst().until() <= tick) queue.removeFirst();
        if (!queue.isEmpty()) return queue.getFirst().text();
        pending.remove(player);
        return null;
    }

    public void clear(UUID player) {
        pending.remove(player);
    }

    public void clear() {
        pending.clear();
    }
}
