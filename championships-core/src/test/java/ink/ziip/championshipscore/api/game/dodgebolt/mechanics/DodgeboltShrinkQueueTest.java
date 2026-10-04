package ink.ziip.championshipscore.api.game.dodgebolt.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DodgeboltShrinkQueueTest {
    @Test
    void eliminationShrinkMatchesMccSequence() {
        assertEquals(2, DodgeboltShrinkQueue.layersForElimination(1));
        assertEquals(2, DodgeboltShrinkQueue.layersForElimination(2));
        assertEquals(1, DodgeboltShrinkQueue.layersForElimination(3));
        assertEquals(1, DodgeboltShrinkQueue.layersForElimination(4));
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
