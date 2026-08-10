package ink.ziip.championshipscore.api.daily.adapter;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.daily.DailyGameAdapter;
import ink.ziip.championshipscore.api.daily.DailyRules;
import ink.ziip.championshipscore.api.game.acerace.AceRaceArea;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/** Chooses one idle lightweight runtime slot; all slots may share the exact physical track. */
public final class AceRaceDailyGameAdapter implements DailyGameAdapter {
    private final ChampionshipsCore plugin;

    public AceRaceDailyGameAdapter(ChampionshipsCore plugin) { this.plugin = plugin; }

    @Override public @NotNull GameTypeEnum game() { return GameTypeEnum.AceRace; }

    @Override
    public @NotNull DailyRules rules() {
        return new DailyRules(CCConfig.DAILY_ACERACE_MIN_PLAYERS, CCConfig.DAILY_ACERACE_MAX_PLAYERS,
                CCConfig.DAILY_ACERACE_TEAM_SIZE, CCConfig.DAILY_ACERACE_TEAMS,
                CCConfig.DAILY_ACERACE_COUNTDOWN_SECONDS);
    }

    @Override
    public @Nullable StartResult start(@NotNull List<ChampionshipTeam> teams) {
        List<AceRaceArea> candidates = plugin.getGameManager().getAceRaceManager().getRuntimeInstances().stream()
                .filter(area -> area.getGameStageEnum() == GameStageEnum.WAITING)
                .sorted(Comparator.comparing((AceRaceArea area) -> area.getGameConfig().getConfigName(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(AceRaceArea::getCopyIndex))
                .toList();
        for (AceRaceArea area : candidates) {
            if (plugin.getGameManager().joinMultiTeamInstanceForTeams(GameTypeEnum.AceRace, area,
                    false, GameRunMode.DAILY, teams)) {
                return new StartResult(area.getGameConfig().getConfigName(), area);
            }
        }
        return null;
    }
}
