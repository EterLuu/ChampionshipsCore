package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.protocol.BingoTaskSpec;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class WorkerObjectivesCompatibilityTest {
    @Test
    void rejectsNewMaterialsBeforeResolvingServerRegistries() {
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
                                                        Map.of("material", "STRAW_BED")))));
        assertTrue(failure.getMessage().contains("26.2 worker content baseline"));
    }

    @Test
    void rejectsNewStatisticsBeforeReadingPlayerProgress() {
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
                                                                "SLEEP_IN_STRAW_BED")))));
        assertTrue(failure.getMessage().contains("26.2 worker content baseline"));
    }
}
