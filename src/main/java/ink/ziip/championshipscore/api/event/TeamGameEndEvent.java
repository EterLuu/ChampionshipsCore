package ink.ziip.championshipscore.api.event;

import ink.ziip.championshipscore.api.game.area.team.BaseTeamArea;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TeamGameEndEvent extends ChampionshipsCoreEvent {
    private final ChampionshipTeam rightChampionshipTeam;
    private final ChampionshipTeam leftChampionshipTeam;
    private final BaseTeamArea baseTeamArea;
}
