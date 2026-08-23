package ink.ziip.championshipscore.api.daily.adapter;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.daily.DailyGameAdapter;
import ink.ziip.championshipscore.api.daily.DailyRules;
import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorTeamArea;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Chooses one idle runtime copy; all copies share the same physical course and stay visually isolated. */
public final class ParkourWarriorDailyGameAdapter implements DailyGameAdapter {
    private final ChampionshipsCore plugin;

    public ParkourWarriorDailyGameAdapter(ChampionshipsCore plugin) { this.plugin = plugin; }

    @Override public @NotNull GameTypeEnum game() { return GameTypeEnum.ParkourWarrior; }

    @Override
    public @NotNull DailyRules rules() {
        return new DailyRules(CCConfig.DAILY_PARKOUR_WARRIOR_MIN_PLAYERS, CCConfig.DAILY_PARKOUR_WARRIOR_MAX_PLAYERS,
                CCConfig.DAILY_PARKOUR_WARRIOR_TEAM_SIZE, CCConfig.DAILY_PARKOUR_WARRIOR_TEAMS,
                CCConfig.DAILY_PARKOUR_WARRIOR_COUNTDOWN_SECONDS);
    }

    @Override
    public int availableSlots() {
        return (int) candidates().stream().count();
    }

    @Override
    public @NotNull CompletionStage<StartResult> start(@NotNull List<ChampionshipTeam> teams) {
        for (ParkourWarriorTeamArea area : candidates()) {
            if (plugin.getGameManager().joinMultiTeamInstanceForTeams(GameTypeEnum.ParkourWarrior, area,
                    false, GameRunMode.DAILY, teams)) {
                return CompletableFuture.completedFuture(new StartResult(area.getGameConfig().getConfigName(), area));
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    private @NotNull List<ParkourWarriorTeamArea> candidates() {
        return plugin.getGameManager().getParkourWarriorManager().getAreaNameList().stream()
                .flatMap(map -> plugin.getGameManager().getParkourWarriorManager().getMapInstances(map).stream())
                .filter(area -> area.getGameStageEnum() == GameStageEnum.WAITING)
                .filter(area -> plugin.getDailyManager().session(area) == null)
                .filter(area -> plugin.getPrepareSessionManager().canStart(GameTypeEnum.ParkourWarrior,
                        area.getGameConfig().getConfigName()))
                .sorted(Comparator.comparing((ParkourWarriorTeamArea area) -> area.getGameConfig().getConfigName(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(ParkourWarriorTeamArea::getCopyIndex))
                .toList();
    }
}
