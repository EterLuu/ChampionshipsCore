package ink.ziip.championshipscore.api.game.riptiderush;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A consumed shield exempts only wrong answers in the current seven-round floor stage. */
final class RiptideFloorProtection {
    private final Set<UUID> players = new HashSet<>();
    void grant(UUID player) { players.add(player); }
    boolean protects(UUID player) { return players.contains(player); }
    void clear() { players.clear(); }
}
