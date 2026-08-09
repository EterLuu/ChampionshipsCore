package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.bingo.engine.BingoResult;
import ink.ziip.championshipscore.protocol.TeamSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Selects the stable per-viewer ranking rows used by the Folia sidebar. */
final class WorkerSidebarRanking {
    static final int LEADER_LIMIT = 8;

    private WorkerSidebarRanking() {
    }

    static List<Entry> select(BingoResult result, Map<Integer, TeamSnapshot> teams, Integer viewerTeamId) {
        List<Integer> ranked = result.rankedTeamIds().stream().filter(teams::containsKey).toList();
        List<Entry> selected = new ArrayList<>(LEADER_LIMIT + 1);
        for (int index = 0; index < Math.min(LEADER_LIMIT, ranked.size()); index++) {
            int teamId = ranked.get(index);
            selected.add(new Entry(teams.get(teamId), index + 1, java.util.Objects.equals(teamId, viewerTeamId)));
        }
        if (viewerTeamId != null && selected.stream().noneMatch(entry -> entry.team().id() == viewerTeamId)) {
            int index = ranked.indexOf(viewerTeamId);
            if (index >= 0) selected.add(new Entry(teams.get(viewerTeamId), index + 1, true));
        }
        return List.copyOf(selected);
    }

    record Entry(TeamSnapshot team, int rank, boolean viewerTeam) {
    }
}
