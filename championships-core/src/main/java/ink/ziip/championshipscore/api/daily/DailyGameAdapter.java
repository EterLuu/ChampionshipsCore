package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Small game-specific boundary; queueing, parties, teams, persistence and UI stay reusable. */
public interface DailyGameAdapter {
    @NotNull GameTypeEnum game();
    @NotNull DailyRules rules();
    /** Number of runtime slots that can accept a new session right now. */
    int availableSlots();
    @Nullable StartResult start(@NotNull List<ChampionshipTeam> teams);

    record StartResult(String map, BaseGameInstance instance) {}
}
