package ink.ziip.championshipscore.api.game.dodgebolt.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DodgeboltShrinkQueueTest {
    @ParameterizedTest(name = "elimination {0} shrinks {1} layers")
    @CsvSource({"1, 2", "2, 2", "3, 1", "4, 1"})
    void eliminationShrinkMatchesMccSequence(int elimination, int layers) {
        assertEquals(layers, DodgeboltShrinkQueue.layersForElimination(elimination));
    }

    @Test
    void twoLayerEliminationIsOneShrinkEvent() {
        DodgeboltShrinkQueue queue = new DodgeboltShrinkQueue();

        assertEquals(2, queue.enqueue(2, 8));
        assertEquals(2, queue.currentLayers());
        assertEquals(2, queue.completeCurrent());
        assertTrue(queue.isEmpty());
    }

    @Test
    void consecutiveTriggersKeepTheirOwnLayerCounts() {
        DodgeboltShrinkQueue queue = new DodgeboltShrinkQueue();

        queue.enqueue(1, 8); // Six shots.
        queue.enqueue(2, 7); // One of the first two eliminations.

        assertEquals(1, queue.completeCurrent());
        assertEquals(2, queue.currentLayers());
        assertEquals(2, queue.completeCurrent());
        assertTrue(queue.isEmpty());
    }

    @Test
    void eventIsClippedToRemainingPlatformLayers() {
        DodgeboltShrinkQueue queue = new DodgeboltShrinkQueue();

        assertEquals(1, queue.enqueue(2, 1));
        assertEquals(1, queue.queuedLayers());
        assertEquals(1, queue.completeCurrent());
    }
}
