package ink.ziip.championshipscore.api.daily.adapter;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.daily.DailyGameAdapter;
import ink.ziip.championshipscore.api.daily.DailyRules;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Explicit-roster bridge for both local and remote Bingo execution. */
public final class BingoDailyGameAdapter implements DailyGameAdapter {
    private final ChampionshipsCore plugin;

    public BingoDailyGameAdapter(ChampionshipsCore plugin) { this.plugin = plugin; }

    @Override public @NotNull GameTypeEnum game() { return GameTypeEnum.Bingo; }

    @Override
    public @NotNull DailyRules rules() {
        return new DailyRules(CCConfig.DAILY_BINGO_MIN_PLAYERS, CCConfig.DAILY_BINGO_MAX_PLAYERS,
                CCConfig.DAILY_BINGO_TEAM_SIZE, CCConfig.DAILY_BINGO_TEAMS,
                CCConfig.DAILY_BINGO_COUNTDOWN_SECONDS);
    }

    @Override
    public int availableSlots() {
        return plugin.getDailyManager().isGameRunning(GameTypeEnum.Bingo) ? 0 : 1;
    }

    @Override
    public @NotNull CompletionStage<StartResult> start(@NotNull List<ChampionshipTeam> teams) {
        return plugin.getDailyManager().beginBingoVote(teams)
                .thenCompose(variant -> variant == null
                        ? CompletableFuture.completedFuture(null)
                        : start(teams, variant));
    }

    private @NotNull CompletionStage<StartResult> start(
            @NotNull List<ChampionshipTeam> teams,
            @NotNull ink.ziip.championshipscore.protocol.BingoVariantRules variant) {
        List<String> maps = plugin.getGameManager().getBingoManager().getAreaNameList().stream().sorted().toList();
        CompletionStage<StartResult> attempt = CompletableFuture.completedFuture(null);
        for (String map : maps) {
            attempt = attempt.thenCompose(started -> {
                if (started != null) return CompletableFuture.completedFuture(started);
                return plugin.getGameManager().joinBingoForTeams(map, false, GameRunMode.DAILY, teams, variant)
                        .thenApply(accepted -> {
                            if (!accepted) return null;
                            BaseGameInstance instance = teams.getFirst().getMembers().stream().findFirst()
                                    .map(plugin.getGameManager()::getBasePlayerArea).orElse(null);
                            return instance == null ? null : new StartResult(map, instance);
                        });
            });
        }
        return attempt;
    }
}
