package ink.ziip.championshipscore.api.game.bingo.execution;

import ink.ziip.championshipscore.api.object.game.GameRunMode;

import java.util.Objects;

/** SCC-side request before either the local instance or a remote worker owns the game. */
public record BingoStartRequest(String area, boolean showIntroduction, GameRunMode runMode) {
    public BingoStartRequest {
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(runMode, "runMode");
        if (area.isBlank()) throw new IllegalArgumentException("area must not be blank");
        // The worker has no BaseGameInstance guard, so normalize the same lifecycle invariant at
        // the execution boundary before the value is frozen into a remote manifest.
        showIntroduction = runMode == GameRunMode.EVENT && showIntroduction;
    }
}
