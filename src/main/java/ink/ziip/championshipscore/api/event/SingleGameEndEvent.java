package ink.ziip.championshipscore.api.event;

import ink.ziip.championshipscore.api.game.area.single.BaseSingleTeamArea;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SingleGameEndEvent extends ChampionshipsCoreEvent {
    private final BaseSingleTeamArea baseSingleTeamArea;
    private final List<ChampionshipTeam> championshipTeams;
}
