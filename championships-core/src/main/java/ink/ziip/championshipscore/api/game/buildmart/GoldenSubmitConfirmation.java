package ink.ziip.championshipscore.api.game.buildmart;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A confirmation applies only to the same player, team, slot and order generation. */
final class GoldenSubmitConfirmation {
    private record Pending(Object team, Object slot, long generation, long armedAt) {}
    private final Map<UUID, Pending> pending = new HashMap<>();

    boolean confirm(UUID player, Object team, Object slot, long generation, long nowMillis) {
        Pending previous = pending.remove(player);
        if (previous != null && previous.team == team && previous.slot == slot
                && previous.generation == generation && nowMillis > previous.armedAt
                && nowMillis - previous.armedAt < 5000) return true;
        pending.put(player, new Pending(team, slot, generation, nowMillis));
        return false;
    }

    void remove(UUID player) { pending.remove(player); }
    void clear() { pending.clear(); }
}
