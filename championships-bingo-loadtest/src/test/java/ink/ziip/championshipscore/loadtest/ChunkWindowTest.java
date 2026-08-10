package ink.ziip.championshipscore.loadtest;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkWindowTest {
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
}
