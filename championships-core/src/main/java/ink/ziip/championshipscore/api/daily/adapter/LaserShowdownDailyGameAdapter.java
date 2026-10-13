package ink.ziip.championshipscore.api.daily.adapter;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.daily.DailyGameAdapter;
import ink.ziip.championshipscore.api.daily.DailyRules;
import ink.ziip.championshipscore.api.game.lasershowdown.runtime.LaserShowdownTeamArea;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** One DAILY match uses one published map instance and one of its physical sub-arenas. */
public final class LaserShowdownDailyGameAdapter implements DailyGameAdapter {
    private final ChampionshipsCore plugin;

    public LaserShowdownDailyGameAdapter(ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull GameTypeEnum game() {
        return GameTypeEnum.LaserShowdown;
    }

    @Override
    public @NotNull DailyRules rules() {
        return new DailyRules(
                Math.max(2, CCConfig.DAILY_LASER_SHOWDOWN_MIN_PLAYERS),
                Math.max(2, Math.min(16, CCConfig.DAILY_LASER_SHOWDOWN_MAX_PLAYERS)),
                1,
                Math.max(2, Math.min(16, CCConfig.DAILY_LASER_SHOWDOWN_TEAMS)),
                CCConfig.DAILY_LASER_SHOWDOWN_COUNTDOWN_SECONDS);
    }

    @Override
    public int availableSlots() {
        return candidates().size();
    }

    @Override
    public @NotNull CompletionStage<StartResult> start(@NotNull List<ChampionshipTeam> teams) {
        if (teams.size() < 2) return CompletableFuture.completedFuture(null);
        for (LaserShowdownTeamArea area : candidates()) {
            if (plugin.getGameManager()
                    .joinMultiTeamInstanceForTeams(game(), area, false, GameRunMode.DAILY, teams)) {
                return CompletableFuture.completedFuture(
                        new StartResult(area.getGameConfig().getConfigName(), area));
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    private @NotNull List<LaserShowdownTeamArea> candidates() {
        return plugin.getGameManager().getLaserShowdownManager().getRuntimeInstances().stream()
                .filter(area -> area.getGameStageEnum() == GameStageEnum.WAITING)
                .filter(area -> plugin.getDailyManager().session(area) == null)
                .filter(area -> !area.getPlayableArenaIndices().isEmpty())
                .filter(
                        area ->
                                plugin.getPrepareSessionManager()
                                        .canStart(game(), area.getGameConfig().getConfigName()))
                .sorted(
                        Comparator.comparing(
                                (LaserShowdownTeamArea area) ->
                                        area.getGameConfig().getConfigName(),
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
