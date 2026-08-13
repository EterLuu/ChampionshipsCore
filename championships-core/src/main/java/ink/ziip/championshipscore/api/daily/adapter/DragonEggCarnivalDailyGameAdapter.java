package ink.ziip.championshipscore.api.daily.adapter;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.daily.DailyGameAdapter;
import ink.ziip.championshipscore.api.daily.DailyRules;
import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalArea;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Starts one idle published End arena with exactly two transient DAILY teams. */
public final class DragonEggCarnivalDailyGameAdapter implements DailyGameAdapter {
    private final ChampionshipsCore plugin;

    public DragonEggCarnivalDailyGameAdapter(@NotNull ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull GameTypeEnum game() {
        return GameTypeEnum.DragonEggCarnival;
    }

    @Override
    public @NotNull DailyRules rules() {
        return new DailyRules(CCConfig.DAILY_DRAGON_EGG_CARNIVAL_MIN_PLAYERS,
                CCConfig.DAILY_DRAGON_EGG_CARNIVAL_MAX_PLAYERS,
                CCConfig.DAILY_DRAGON_EGG_CARNIVAL_TEAM_SIZE,
                CCConfig.DAILY_DRAGON_EGG_CARNIVAL_TEAMS,
                CCConfig.DAILY_DRAGON_EGG_CARNIVAL_COUNTDOWN_SECONDS);
    }

    @Override
    public int availableSlots() {
        return candidates().size();
    }

    @Override
    public @NotNull CompletionStage<StartResult> start(@NotNull List<ChampionshipTeam> teams) {
        if (teams.size() != 2) return CompletableFuture.completedFuture(null);
        for (DragonEggCarnivalArea area : candidates()) {
            if (plugin.getGameManager().joinTeamArea(GameTypeEnum.DragonEggCarnival,
                    area.getGameConfig().getConfigName(), teams.get(0), teams.get(1), false, GameRunMode.DAILY)) {
                return CompletableFuture.completedFuture(new StartResult(area.getGameConfig().getConfigName(), area));
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    private @NotNull List<DragonEggCarnivalArea> candidates() {
        return plugin.getGameManager().getDragonEggCarnivalManager().getRuntimeInstances().stream()
                .filter(area -> area.getGameStageEnum() == GameStageEnum.WAITING)
                .filter(area -> plugin.getDailyManager().session(area) == null)
                .filter(area -> plugin.getPrepareSessionManager().canStart(GameTypeEnum.DragonEggCarnival,
                        area.getGameConfig().getConfigName()))
                .sorted(Comparator.comparing(area -> area.getGameConfig().getConfigName(),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
