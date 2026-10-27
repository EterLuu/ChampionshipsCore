package ink.ziip.championshipscore.worker.seed;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class OverworldBiomeSamplerTest {
    @Test
    void samplesACircleInQuartCoordinatesAndCountsDistinctBiomes() {
        Set<List<Integer>> visited = new HashSet<>();
        var sampler =
                new OverworldBiomeSampler(
                        seed -> {
                            assertEquals(42L, seed);
                            return (x, y, z) -> {
                                visited.add(List.of(x, y, z));
                                return x < 0 ? "forest" : "plains";
                            };
                        });
        assertEquals(2, sampler.score(42L, 4, 4, System.nanoTime() + 1_000_000_000L));
        assertEquals(
                Set.of(
                        List.of(-1, 16, 0),
                        List.of(0, 16, -1),
                        List.of(0, 16, 0),
                        List.of(0, 16, 1),
                        List.of(1, 16, 0)),
                visited);
    }

    @Test
    void elapsedDeadlineRejectsPartialResultsAndDoesNotBuildAnotherCandidate() {
        AtomicInteger builds = new AtomicInteger();
        var sampler =
                new OverworldBiomeSampler(
                        seed -> {
                            builds.incrementAndGet();
                            return (x, y, z) -> "plains";
                        });
        assertThrows(
                OverworldBiomeSampler.SeedFilterTimeoutException.class,
                () -> sampler.score(0, 2000, 32, System.nanoTime() - 1));
        assertEquals(0, builds.get());
    }

    @Test
    void rejectsInvalidWindowAndStepBeforePreparingNativeNoise() {
        var sampler =
                new OverworldBiomeSampler(
                        seed -> {
                            throw new AssertionError("Unexpected native state");
                        });
        for (int[] input :
                List.of(new int[] {0, 4}, new int[] {6, 4}, new int[] {4, 0}, new int[] {4, 6})) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> sampler.score(0, input[0], input[1], Long.MAX_VALUE));
        }
    }
}
