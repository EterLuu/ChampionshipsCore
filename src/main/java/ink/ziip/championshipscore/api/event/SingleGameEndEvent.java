package ink.ziip.championshipscore.api.event;

import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;

import java.util.List;

public class SingleGameEndEvent extends ChampionshipsCoreEvent {
    private final BaseMultiTeamGameInstance gameInstance;
    private final List<ChampionshipTeam> championshipTeams;

    public SingleGameEndEvent(BaseMultiTeamGameInstance gameInstance,
                              List<ChampionshipTeam> championshipTeams) {
        this.gameInstance = gameInstance;
        this.championshipTeams = championshipTeams;
    }

    public BaseMultiTeamGameInstance getGameInstance() {
        return gameInstance;
    }

    public List<ChampionshipTeam> getChampionshipTeams() {
        return championshipTeams;
    }

    /** @deprecated Use {@link #getGameInstance()}. */
    @Deprecated(forRemoval = true)
    public BaseMultiTeamGameInstance getBaseSingleTeamArea() {
        return gameInstance;
    }
}
