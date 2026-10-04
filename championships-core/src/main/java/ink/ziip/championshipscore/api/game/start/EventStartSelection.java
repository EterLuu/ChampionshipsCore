package ink.ziip.championshipscore.api.game.start;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;

import java.util.List;

/** Formal schedules retain the selected teams and map/arena override across every round. */
public record EventStartSelection(String map, List<ChampionshipTeam> teams, ArenaSelection arenas) {
    public EventStartSelection {
        teams = List.copyOf(teams);
    }
}
