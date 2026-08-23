package ink.ziip.championshipscore.api.game.bingo.execution;

import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.protocol.BingoVariantRules;

import java.util.List;
import java.util.Objects;

/** SCC-side request before either the local instance or a remote worker owns the game. */
public record BingoStartRequest(String area, boolean showIntroduction, GameRunMode runMode,
                                List<ChampionshipTeam> teams, BingoVariantRules variant) {
    public BingoStartRequest(String area, boolean showIntroduction, GameRunMode runMode) {
        this(area, showIntroduction, runMode, List.of(), BingoVariantRules.FIXED_POINTS);
    }

    public BingoStartRequest(String area, boolean showIntroduction, GameRunMode runMode,
                             List<ChampionshipTeam> teams) {
        this(area, showIntroduction, runMode, teams, BingoVariantRules.FIXED_POINTS);
    }

    public BingoStartRequest {
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(runMode, "runMode");
        Objects.requireNonNull(variant, "variant");
        teams = List.copyOf(Objects.requireNonNull(teams, "teams"));
        if (area.isBlank()) throw new IllegalArgumentException("area must not be blank");
        // The worker has no BaseGameInstance guard, so normalize the same lifecycle invariant at
        // the execution boundary before the value is frozen into a remote manifest.
        showIntroduction = runMode == GameRunMode.EVENT && showIntroduction;
        if (runMode != GameRunMode.DAILY) variant = BingoVariantRules.FIXED_POINTS;
    }
}
