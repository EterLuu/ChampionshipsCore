package ink.ziip.championshipscore.api.daily;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.api.daily.entry.DailyRecordEntry;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

class DailyModelsTest {
    @Nested
    class DailyMapStatCases {
        @Test
        void mapStatsKeepPeaksAndSumCountersLikeTheSqlUpsert() {
            var first = new DailyMapStat(1, 1, 1, 18, 2, 5, 210.5, 1, 0, 0, 32, 1);
            var second = new DailyMapStat(1, 0, 1, 22, 1, 3, 180.25, 0, 1, 1, 27, 0);
            assertEquals(
                    new DailyMapStat(2, 1, 2, 22, 2, 5, 210.5, 1, 1, 1, 32, 1),
                    first.merge(second));
        }
    }

    @ExtendWith(ConfigurationStateExtension.class)
    @Nested
    class DailyMetricCases {
        @BeforeAll
        static void loadGuiMetricFormats() throws Exception {
            YamlConfiguration gui =
                    YamlConfiguration.loadConfiguration(
                            Path.of("src/main/resources/gui.yml").toFile());
            Field active = GuiConfig.class.getDeclaredField("active");
            active.setAccessible(true);
            active.set(null, gui);
        }

        @Test
        void boardIdsSeparateMapScopesFromTheOverallAggregate() {
            assertEquals(
                    "acerace_fastest_lap_overall", DailyMetric.ACERACE_FASTEST_LAP.boardId(null));
            assertEquals(
                    "acerace_fastest_lap_map_end_plains",
                    DailyMetric.ACERACE_FASTEST_LAP.boardId("End Plains"));
            assertEquals(
                    "dragon_first_liberate_rate_map_a",
                    DailyMetric.DRAGON_FIRST_LIBERATE_RATE.boardId("A"));
        }

        @Test
        void everyGameListsItsMetricsInDisplayOrderAndFormatsValues() {
            assertEquals(
                    List.of(
                            DailyMetric.BINGO_MAX_TASKS,
                            DailyMetric.BINGO_MAX_LINES,
                            DailyMetric.BINGO_MAX_FIRSTS),
                    DailyMetric.forGame(GameTypeEnum.Bingo));
            assertEquals(
                    List.of(
                            DailyMetric.ACERACE_FASTEST_LAP,
                            DailyMetric.ACERACE_FASTEST_THREE_LAPS),
                    DailyMetric.forGame(GameTypeEnum.AceRace));
            assertEquals(4, DailyMetric.forGame(GameTypeEnum.DragonEggCarnival).size());
            assertEquals(
                    List.of(DailyMetric.PKW_STARS_TIME, DailyMetric.PKW_POINTS_TIME),
                    DailyMetric.forGame(GameTypeEnum.ParkourWarrior));
            assertEquals(0, DailyMetric.forGame(GameTypeEnum.TNTRun).size());

            assertEquals("1:02.500", DailyMetric.format(DailyMetric.ACERACE_FASTEST_LAP, 62_500));
            assertEquals("324.7", DailyMetric.format(DailyMetric.DRAGON_MAX_DAMAGE, 324.71));
            assertEquals(
                    "66.7%", DailyMetric.format(DailyMetric.DRAGON_FIRST_GATEWAY_RATE, 66.666));
            assertEquals("18", DailyMetric.format(DailyMetric.BINGO_MAX_TASKS, 18));
            assertEquals(
                    "24⭐ 00:10:22", DailyMetric.format(DailyMetric.PKW_STARS_TIME, 24, 622_000));
            assertEquals(
                    "123分 00:10:22", DailyMetric.format(DailyMetric.PKW_POINTS_TIME, 123, 622_000));
        }

        @Test
        void rateBoardsDemandMoreGamesThanCountBoards() {
            assertEquals(1, DailyMetric.BINGO_MAX_TASKS.leaderboardMinGames());
            assertEquals(5, DailyMetric.DRAGON_FIRST_NEXT_GEN_RATE.leaderboardMinGames());
            assertTrue(DailyMetric.ACERACE_FASTEST_LAP.lowerBetter());
            assertFalse(DailyMetric.PKW_STARS_TIME.lowerBetter());
            assertFalse(DailyMetric.DRAGON_MAX_DAMAGE.lowerBetter());
        }
    }

    @Nested
    class DailyStatsManagerCases {
        private static DailyRecordEntry attempt(UUID player, long duration, UUID match) {
            return new DailyRecordEntry(
                    player,
                    "player",
                    GameTypeEnum.AceRace,
                    "map",
                    "1",
                    "daily-v1",
                    DailyRecordType.ACERACE_FASTEST_LAP,
                    duration,
                    match,
                    player,
                    duration);
        }

        @Test
        void parkourWarriorCompositeRecordsRankPrimaryValueBeforeTime() {
            assertTrue(DailyStatsManager.isBetterPkwRecord(24, 622_000, 24, 660_000));
            assertFalse(DailyStatsManager.isBetterPkwRecord(23, 500_000, 24, 660_000));
            assertTrue(DailyStatsManager.isBetterPkwRecord(25, 900_000, 24, 660_000));
        }

        @Test
        void personalTimedRecordsKeepThreeBestAttemptsAndAssignRanks() {
            UUID player = UUID.randomUUID();
            List<DailyRecordEntry> attempts =
                    List.of(
                            attempt(player, 90_000, UUID.randomUUID()),
                            attempt(player, 70_000, UUID.randomUUID()),
                            attempt(player, 80_000, UUID.randomUUID()),
                            attempt(player, 60_000, UUID.randomUUID()),
                            attempt(player, 50_000, UUID.randomUUID()));

            List<DailyRecordEntry> top = DailyStatsManager.topRecords(attempts);

            assertEquals(
                    List.of(50_000L, 60_000L, 70_000L),
                    top.stream().map(DailyRecordEntry::durationMs).toList());
            assertEquals(List.of(1, 2, 3), top.stream().map(DailyRecordEntry::recordRank).toList());
        }
    }

    @Nested
    class DailyPartyCases {
        @Test
        void selectionIsPartyWideAndLeadershipTransfersOnLeave() {
            UUID creator = UUID.randomUUID();
            UUID member = UUID.randomUUID();
            DailyParty party = new DailyParty(creator);
            assertTrue(party.add(member));

            long revision = party.select(GameTypeEnum.Bingo);
            assertEquals(GameTypeEnum.Bingo, party.selectedGame());
            assertEquals(revision, party.revision());

            assertTrue(party.remove(creator));
            assertEquals(member, party.leader());
        }
    }

    @Nested
    class DailyRulesCases {
        @Test
        void rulesClampInvalidConfigurationToUsableCapacity() {
            DailyRules rules = new DailyRules(99, 99, 2, 3, 0);

            assertEquals(6, rules.maxPlayers());
            assertEquals(6, rules.minPlayers());
            assertEquals(3, rules.countdownSeconds());
        }
    }

    @Nested
    class DailyStatSnapshotCases {
        @Test
        void statsAccumulateProgressAndKeepTheLargestSingleMatchCount() {
            var snapshot = DailyStatSnapshot.EMPTY.add(true, 3, 25).add(false, 1, 14);
            assertEquals(new DailyStatSnapshot(2, 1, 4, 39, 25), snapshot);
        }
    }
}
