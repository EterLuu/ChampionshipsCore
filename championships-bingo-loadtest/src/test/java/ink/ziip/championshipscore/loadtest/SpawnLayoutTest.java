package ink.ziip.championshipscore.loadtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

class SpawnLayoutTest {
    @Test
    void viewDistanceWindowHasExpectedAreaAcrossNegativeCoordinates() {
        Set<ChunkPos> window = ChunkWindow.around(-0.1, -16.1, 2);

        assertEquals(25, window.size());
        assertTrue(window.contains(new ChunkPos(-1, -2)));
        assertTrue(window.contains(new ChunkPos(-3, -4)));
        assertTrue(window.contains(new ChunkPos(1, 0)));
    }

    @Test
    void movementWithinOneChunkKeepsIdenticalWindow() {
        assertEquals(ChunkWindow.around(1.0, 1.0, 10), ChunkWindow.around(15.9, 15.9, 10));
    }

    @Test
    void stressMixUsesVanillaLandCapRatioWithoutVillagers() {
        assertFalse(ChunkStressController.MONSTER_TYPES.contains(EntityType.VILLAGER));
        assertFalse(ChunkStressController.CREATURE_TYPES.contains(EntityType.VILLAGER));

        int monsters = 0;
        int creatures = 0;
        for (int index = 0; index < 800; index++) {
            EntityType type = ChunkStressController.entityTypeFor(index);
            if (ChunkStressController.MONSTER_TYPES.contains(type)) monsters++;
            if (ChunkStressController.CREATURE_TYPES.contains(type)) creatures++;
        }

        assertEquals(700, monsters);
        assertEquals(100, creatures);
        assertTrue(monsters + creatures == 800);
    }

    @Test
    void distributesSpawnsEvenlyAcrossPlayersInsideVanillaDistanceBand() {
        int[] owners = new int[32];
        for (int sequence = 0; sequence < 3200; sequence++) {
            NaturalSpawnPlanner.Offset offset =
                    NaturalSpawnPlanner.offset(sequence, owners.length, 24.0, 128.0);
            owners[offset.ownerIndex()]++;
            assertTrue(offset.distance() >= 24.0 - 1.0e-9);
            assertTrue(offset.distance() <= 128.0 + 1.0e-9);
        }

        for (int count : owners) assertEquals(100, count);
    }

    @Test
    void separatesEveryMemberEvenAfterRotatingTheTeamLayout() {
        double separation = 384.0;
        for (double angle : new double[] {0.0, Math.PI / 4.0, Math.PI}) {
            StationaryLayout.Point first =
                    StationaryLayout.dispersed(1200.0, -900.0, angle, 0, separation);
            StationaryLayout.Point second =
                    StationaryLayout.dispersed(1200.0, -900.0, angle, 1, separation);
            StationaryLayout.Point third =
                    StationaryLayout.dispersed(1200.0, -900.0, angle, 2, separation);
            StationaryLayout.Point fourth =
                    StationaryLayout.dispersed(1200.0, -900.0, angle, 3, separation);
            Set<StationaryLayout.Point> points = new HashSet<>();
            points.add(first);
            points.add(second);
            points.add(third);
            points.add(fourth);
            assertEquals(4, points.size());
            assertEquals(separation, distance(first, second), 0.000001);
            assertEquals(separation, distance(first, third), 0.000001);
            assertEquals(separation * Math.sqrt(2.0), distance(first, fourth), 0.000001);
        }
    }

    private static double distance(StationaryLayout.Point first, StationaryLayout.Point second) {
        return Math.hypot(first.x() - second.x(), first.z() - second.z());
    }
}
