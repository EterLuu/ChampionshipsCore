package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.bingo.engine.BingoResult;
import ink.ziip.championshipscore.protocol.TeamSnapshot;
import ink.ziip.championshipscore.shared.presentation.RankingWindow;

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
        List<Integer> visible = RankingWindow.select(ranked, viewerTeamId, LEADER_LIMIT);
        List<Entry> selected = new ArrayList<>(LEADER_LIMIT + 1);
        for (int teamId : visible) {
            selected.add(new Entry(teams.get(teamId), ranked.indexOf(teamId) + 1,
                    java.util.Objects.equals(teamId, viewerTeamId)));
        }
        return List.copyOf(selected);
    }

    record Entry(TeamSnapshot team, int rank, boolean viewerTeam) {
    }
}
