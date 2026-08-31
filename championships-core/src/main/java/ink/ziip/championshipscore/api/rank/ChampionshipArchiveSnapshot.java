package ink.ziip.championshipscore.api.rank;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ChampionshipArchiveSnapshot(@NotNull List<TeamScore> teams,
                                          @NotNull List<PlayerScore> players) {
    public record GameScore(@NotNull String gameKey, @NotNull String gameLabel,
                            @NotNull String gameEnglishName, int sortOrder, double score) {
    }

    public record TeamScore(@NotNull String name, int rank, double totalScore,
                            @NotNull List<GameScore> gameScores) {
    }

    public record PlayerScore(@NotNull String name, @NotNull String teamName,
                              double totalScore, boolean isSubstitute,
                              @NotNull List<GameScore> gameScores) {
    }
}
