package ink.ziip.championshipscore.bingo.engine;

import java.util.Objects;
import java.util.UUID;

public record PlayerAward(UUID playerId, int points, String kind) {
    public PlayerAward {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(kind, "kind");
        if (points < 0) throw new IllegalArgumentException("points must be non-negative");
        if (kind.isBlank()) throw new IllegalArgumentException("kind must not be blank");
    }
}
