package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.bingo.engine.BingoResult;
import ink.ziip.championshipscore.protocol.TeamSnapshot;

import org.junit.jupiter.api.Test;

import java.util.*;

class WorkerSidebarRankingTest {
    private static Map<Integer, TeamSnapshot> teams(int count) {
        Map<Integer, TeamSnapshot> teams = new LinkedHashMap<>();
        for (int id = 1; id <= count; id++) {
            teams.put(id, new TeamSnapshot(id, "Team " + id, "WHITE", "&f", List.of()));
        }
        return teams;
    }

    private static BingoResult result(int count) {
        Map<Integer, Integer> scores = new LinkedHashMap<>();
        Map<Integer, Integer> cells = new LinkedHashMap<>();
        Map<Integer, Long> ticks = new LinkedHashMap<>();
        for (int id = 1; id <= count; id++) {
            scores.put(id, (count - id + 1) * 100);
            cells.put(id, count - id);
            ticks.put(id, (long) id);
        }
        return new BingoResult(0, false, scores, cells, ticks, "test-result");
    }

    @Test
    void rankingsKeepTopEightAndHighlightOnlyTheCurrentParticipantTeam() {
        var result = result(10);
        var teams = teams(10);
        for (Integer viewer : Arrays.asList(null, 3, 10)) {
            var rows = WorkerSidebarRanking.select(result, teams, viewer);
            var expected = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8));
            if (Integer.valueOf(10).equals(viewer)) expected.add(10);
            assertEquals(
                    expected,
                    rows.stream().map(row -> row.team().id()).toList(),
                    "viewer=" + viewer);
            assertEquals(
                    viewer == null ? 0 : 1,
                    rows.stream().filter(WorkerSidebarRanking.Entry::viewerTeam).count(),
                    "viewer=" + viewer);
            for (var row : rows) {
                assertEquals(
                        row.team().id(),
                        row.rank(),
                        "viewer=" + viewer + " team=" + row.team().id());
                assertEquals(Objects.equals(viewer, row.team().id()), row.viewerTeam());
            }
        }
    }
}
