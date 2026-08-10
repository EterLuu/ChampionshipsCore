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
    public @Nullable StartResult start(@NotNull List<ChampionshipTeam> teams) {
        List<String> maps = plugin.getGameManager().getBingoManager().getAreaNameList().stream().sorted().toList();
        for (String map : maps) {
            if (!plugin.getGameManager().joinBingoForTeams(map, false, GameRunMode.DAILY, teams)) continue;
            BaseGameInstance instance = teams.getFirst().getMembers().stream().findFirst()
                    .map(plugin.getGameManager()::getBasePlayerArea).orElse(null);
            if (instance != null) return new StartResult(map, instance);
        }
        return null;
    }
}
