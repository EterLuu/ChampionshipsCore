package ink.ziip.championshipscore.loadtest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalSpawnPlannerTest {
    @Test
    void distributesSpawnsEvenlyAcrossPlayersInsideVanillaDistanceBand() {
        int[] owners = new int[32];
        for (int sequence = 0; sequence < 3200; sequence++) {
            NaturalSpawnPlanner.Offset offset = NaturalSpawnPlanner.offset(sequence, owners.length, 24.0, 128.0);
            owners[offset.ownerIndex()]++;
            assertTrue(offset.distance() >= 24.0 - 1.0e-9);
            assertTrue(offset.distance() <= 128.0 + 1.0e-9);
        }

        for (int count : owners) assertEquals(100, count);
    }
}
