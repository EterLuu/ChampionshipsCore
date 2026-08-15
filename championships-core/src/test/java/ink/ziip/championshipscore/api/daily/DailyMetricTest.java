package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyMetricTest {
    @Test
    void mapStatsKeepPeaksAndSumCountersLikeTheSqlUpsert() {
        DailyMapStat first = new DailyMapStat(1, 1, 1, 18, 2, 5, 210.5, 1, 0, 0);
        DailyMapStat second = new DailyMapStat(1, 0, 1, 22, 1, 3, 180.25, 0, 1, 1);

        DailyMapStat merged = first.merge(second);

        assertEquals(2, merged.gamesPlayed());
        assertEquals(1, merged.wins());
        assertEquals(2, merged.trackedGames());
        assertEquals(22, merged.maxTasks());
        assertEquals(2, merged.maxLines());
        assertEquals(5, merged.maxFirstTasks());
        assertEquals(210.5, merged.maxDragonDamage(), 1e-9);
        assertEquals(1, merged.firstLiberate());
        assertEquals(1, merged.firstNextGen());
        assertEquals(1, merged.firstGateway());
    }

    @Test
    void mapCardsCenterInsteadOfFillingFromTheTopLeftCorner() {
        assertEquals(List.of(31), DailyStatsMenu.mapSlots(1));
        assertEquals(List.of(30, 31, 32), DailyStatsMenu.mapSlots(3));
        assertEquals(List.of(28, 29, 30, 31, 32, 33, 34), DailyStatsMenu.mapSlots(7));
        // Eight cards split 4+4 across the first two content rows, each row centered.
        assertEquals(List.of(20, 21, 22, 23, 29, 30, 31, 32), DailyStatsMenu.mapSlots(8));
        // A full page of 21 fills all three rows evenly.
        assertEquals(21, DailyStatsMenu.mapSlots(21).size());
    }

    @Test
    void boardIdsSeparateMapScopesFromTheOverallAggregate() {
        assertEquals("acerace_fastest_lap_overall", DailyMetric.ACERACE_FASTEST_LAP.boardId(null));
        assertEquals("acerace_fastest_lap_map_end_plains",
                DailyMetric.ACERACE_FASTEST_LAP.boardId("End Plains"));
        assertEquals("dragon_first_liberate_rate_map_a", DailyMetric.DRAGON_FIRST_LIBERATE_RATE.boardId("A"));
    }

    @Test
    void everyGameListsItsMetricsInDisplayOrderAndFormatsValues() {
        assertEquals(List.of(DailyMetric.BINGO_MAX_TASKS, DailyMetric.BINGO_MAX_LINES,
                DailyMetric.BINGO_MAX_FIRSTS), DailyMetric.forGame(GameTypeEnum.Bingo));
        assertEquals(List.of(DailyMetric.ACERACE_FASTEST_LAP, DailyMetric.ACERACE_FASTEST_THREE_LAPS),
                DailyMetric.forGame(GameTypeEnum.AceRace));
        assertEquals(4, DailyMetric.forGame(GameTypeEnum.DragonEggCarnival).size());
        assertEquals(0, DailyMetric.forGame(GameTypeEnum.TNTRun).size());

        assertEquals("1:02.500", DailyMetric.format(DailyMetric.ACERACE_FASTEST_LAP, 62_500));
        assertEquals("324.7", DailyMetric.format(DailyMetric.DRAGON_MAX_DAMAGE, 324.71));
        assertEquals("66.7%", DailyMetric.format(DailyMetric.DRAGON_FIRST_GATEWAY_RATE, 66.666));
        assertEquals("18", DailyMetric.format(DailyMetric.BINGO_MAX_TASKS, 18));
    }

    @Test
    void rateBoardsDemandMoreGamesThanCountBoards() {
        assertEquals(1, DailyMetric.BINGO_MAX_TASKS.leaderboardMinGames());
        assertEquals(5, DailyMetric.DRAGON_FIRST_NEXT_GEN_RATE.leaderboardMinGames());
        assertEquals(true, DailyMetric.ACERACE_FASTEST_LAP.lowerBetter());
        assertEquals(false, DailyMetric.DRAGON_MAX_DAMAGE.lowerBetter());
    }
}
