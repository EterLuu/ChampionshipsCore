package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.bingo.engine.BingoResult;
import ink.ziip.championshipscore.protocol.TeamSnapshot;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerSidebarRankingTest {
    @Test
    void participantSeesTopEightAndTheirOwnOutOfRangeTeam() {
        Map<Integer, TeamSnapshot> teams = teams(10);
        BingoResult result = result(10);

        List<WorkerSidebarRanking.Entry> rows = WorkerSidebarRanking.select(result, teams, 10);

        assertEquals(9, rows.size());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 10),
                rows.stream().map(row -> row.team().id()).toList());
        assertEquals(10, rows.getLast().rank());
        assertTrue(rows.getLast().viewerTeam());
    }

    @Test
    void participantTeamInsideTopEightIsHighlightedWithoutDuplication() {
        List<WorkerSidebarRanking.Entry> rows = WorkerSidebarRanking.select(result(10), teams(10), 3);

        assertEquals(8, rows.size());
        assertTrue(rows.get(2).viewerTeam());
        assertEquals(1, rows.stream().filter(WorkerSidebarRanking.Entry::viewerTeam).count());
    }

    @Test
    void spectatorSeesOnlyTopEightWithoutHighlight() {
        List<WorkerSidebarRanking.Entry> rows = WorkerSidebarRanking.select(result(10), teams(10), null);

        assertEquals(8, rows.size());
        assertTrue(rows.stream().noneMatch(WorkerSidebarRanking.Entry::viewerTeam));
        assertFalse(rows.isEmpty());
    }

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
}
