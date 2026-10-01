package ink.ziip.championshipscore.worker.seedlab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeedLab26_2Test {
    @Test
    void matchesKnownOverworldBiomeQueries() throws Exception {
        SeedLab26_2 predictor = SeedLab26_2.instance();

        assertEquals("minecraft:river", predictor.biome(123456789L, 0, 64, 0));
        assertEquals("minecraft:river", predictor.biome(123456789L, 1, 64, 0));
        assertEquals("minecraft:old_growth_birch_forest", predictor.biome(123456789L, 1_000, 64, 1_000));
    }

    @Test
    void scoresTheConfiguredSpawnWindowBeforeDeadline() throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        int score = SeedLab26_2.instance().score(123456789L, 2_000, 32, deadline);

        assertTrue(score > 1);
    }
}
