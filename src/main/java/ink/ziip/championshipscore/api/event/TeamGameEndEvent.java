package ink.ziip.championshipscore.api.event;

import ink.ziip.championshipscore.api.game.instance.paired.BasePairedGameInstance;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;

public class TeamGameEndEvent extends ChampionshipsCoreEvent {
    private final ChampionshipTeam rightChampionshipTeam;
    private final ChampionshipTeam leftChampionshipTeam;
    private final BasePairedGameInstance gameInstance;

    public TeamGameEndEvent(ChampionshipTeam rightChampionshipTeam, ChampionshipTeam leftChampionshipTeam,
                            BasePairedGameInstance gameInstance) {
        this.rightChampionshipTeam = rightChampionshipTeam;
        this.leftChampionshipTeam = leftChampionshipTeam;
        this.gameInstance = gameInstance;
    }

    public ChampionshipTeam getRightChampionshipTeam() {
        return rightChampionshipTeam;
    }

    public ChampionshipTeam getLeftChampionshipTeam() {
        return leftChampionshipTeam;
    }

    public BasePairedGameInstance getGameInstance() {
        return gameInstance;
    }
}
