package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import java.util.*;

/**
 * One round's actual starters and atomic elimination batches; no Bukkit or persistent team state.
 */
final class RiptideRoundLedger {
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Set<UUID> pending = new LinkedHashSet<>();
    private final List<List<UUID>> groups = new ArrayList<>();

    void start(Collection<UUID> starters) {
        clear();
        alive.addAll(starters);
    }

    void eliminate(Collection<UUID> candidates) {
        for (UUID player : candidates) if (alive.remove(player)) pending.add(player);
    }

    /** All deaths since the last course tick share a rank and cannot score off one another. */
    Map<UUID, Integer> flush() {
        if (pending.isEmpty()) return Map.of();
        int points = pending.size() * 4;
        Map<UUID, Integer> awards = new LinkedHashMap<>();
        alive.forEach(player -> awards.put(player, points));
        groups.add(List.copyOf(pending));
        pending.clear();
        return Map.copyOf(awards);
    }

    Map<UUID, Integer> placementBonuses() {
        if (!pending.isEmpty())
            throw new IllegalStateException("Flush eliminations before settlement");
        return RiptideRushScoring.placementBonuses(alive, groups);
    }

    Collection<UUID> winners() {
        if (!pending.isEmpty())
            throw new IllegalStateException("Flush eliminations before settlement");
        return !alive.isEmpty()
                ? List.copyOf(alive)
                : groups.isEmpty() ? List.of() : groups.getLast();
    }

    void clear() {
        alive.clear();
        pending.clear();
        groups.clear();
    }
}
