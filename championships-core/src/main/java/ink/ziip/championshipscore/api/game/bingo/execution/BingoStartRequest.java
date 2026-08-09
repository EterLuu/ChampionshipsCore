package ink.ziip.championshipscore.api.game.bingo.execution;

import ink.ziip.championshipscore.api.object.game.GameRunMode;

import java.util.Objects;

/** SCC-side request before either the local instance or a remote worker owns the game. */
public record BingoStartRequest(String area, boolean showIntroduction, GameRunMode runMode) {
    public BingoStartRequest {
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(runMode, "runMode");
        if (area.isBlank()) throw new IllegalArgumentException("area must not be blank");
    }
}
