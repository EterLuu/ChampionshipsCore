package ink.ziip.championshipscore.api.event;

import ink.ziip.championshipscore.api.game.area.team.BaseTeamArea;
import ink.ziip.championshipscore.api.team.Team;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TeamGameEndEvent extends ChampionshipsCoreEvent {
    private final Team rightTeam;
    private final Team leftTeam;
    private final BaseTeamArea baseTeamArea;
}
