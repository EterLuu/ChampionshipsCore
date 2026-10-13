package ink.ziip.championshipscore.api.game.bingo.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ink.ziip.championshipscore.api.game.bingo.task.pool.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class RemoteBingoContentTest {
    @Test
    void filtersExpandedAndExplicitNewObjectivesBeforeSampling() {
        PoolEntrySpec oldBed = entry(PoolEntrySpec.Kind.ITEM, "RED_BED");
        PoolEntrySpec oldStatistic = entry(PoolEntrySpec.Kind.STAT, "SLEEP_IN_BED");
        TaskPoolSpec source =
                new TaskPoolSpec(
                        List.of(
                                oldBed,
                                entry(PoolEntrySpec.Kind.ITEM, "STRAW_BED"),
                                oldStatistic,
                                entry(PoolEntrySpec.Kind.STAT, "SLEEP_IN_STRAW_BED"),
                                entry(PoolEntrySpec.Kind.CRAFT, "POPLAR_PLANKS"),
                                entry(PoolEntrySpec.Kind.ADVANCEMENT, "adventure/future_objective"),
                                PoolEntrySpec.allOf(
                                        List.of("RED_BED", "STRAW_BED"),
                                        "RED_BED",
                                        "beds",
                                        1,
                                        Difficulty.MEDIUM,
                                        Dimension.OVERWORLD,
                                        "beds")));
        assertEquals(List.of(oldBed, oldStatistic), RemoteBingoContent.filter(source).entries());
        assertEquals(7, source.entries().size());
    }

    @Test
    void retainsOldMembersOfExpandedAnyCollections() {
        PoolEntrySpec source =
                PoolEntrySpec.oneOf(
                        List.of("RED_BED", "STRAW_BED"),
                        "RED_BED",
                        "beds",
                        1,
                        Difficulty.MEDIUM,
                        Dimension.OVERWORLD,
                        "beds");
        PoolEntrySpec expected =
                PoolEntrySpec.oneOf(
                        List.of("RED_BED"),
                        "RED_BED",
                        "beds",
                        1,
                        Difficulty.MEDIUM,
                        Dimension.OVERWORLD,
                        "beds");
        assertEquals(
                List.of(expected),
                RemoteBingoContent.filter(new TaskPoolSpec(List.of(source))).entries());
    }

    private static PoolEntrySpec entry(PoolEntrySpec.Kind kind, String key) {
        return new PoolEntrySpec(kind, key, 1, Difficulty.MEDIUM, Dimension.OVERWORLD, null);
    }
}
