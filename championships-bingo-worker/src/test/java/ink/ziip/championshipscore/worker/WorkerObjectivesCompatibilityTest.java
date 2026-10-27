package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.protocol.BingoTaskSpec;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class WorkerObjectivesCompatibilityTest {
    @Test
    void acceptsMaterialsAndStatisticsAvailableOnTheDeployedWorker() {
        assertDoesNotThrow(
                () ->
                        new WorkerObjectives(
                                List.of(
                                        new BingoTaskSpec(
                                                0,
                                                "straw-bed",
                                                "item",
                                                Map.of("material", "STRAW_BED")),
                                        new BingoTaskSpec(
                                                1,
                                                "straw-sleep",
                                                "statistic",
                                                Map.of("statistic", "SLEEP_IN_STRAW_BED")))));
    }

    @Test
    void rejectsUnknownMaterialsBeforeResolvingServerRegistries() {
        var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new WorkerObjectives(
                                        List.of(
                                                new BingoTaskSpec(
                                                        0,
                                                        "straw-bed",
                                                        "item",
                                                        Map.of("material", "UNKNOWN_MATERIAL")))));
        assertTrue(failure.getMessage().contains("26.3 worker content baseline"));
    }

    @Test
    void rejectsUnknownStatisticsBeforeReadingPlayerProgress() {
        var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new WorkerObjectives(
                                        List.of(
                                                new BingoTaskSpec(
                                                        0,
                                                        "straw-bed-sleep",
                                                        "statistic",
                                                        Map.of(
                                                                "statistic",
                                                                "UNKNOWN_STATISTIC")))));
        assertTrue(failure.getMessage().contains("26.3 worker content baseline"));
    }
}
