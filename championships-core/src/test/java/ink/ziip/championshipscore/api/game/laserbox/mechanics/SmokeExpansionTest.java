package ink.ziip.championshipscore.api.game.laserbox.mechanics;

import static ink.ziip.championshipscore.support.TestObjects.proxy;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class SmokeExpansionTest {
    @Test
    void placedCellsAreNotScannedAgainButObstructionsCanClearLater() {
        List<Block> cells = new ArrayList<>();
        for (int i = 0; i < 180; i++) cells.add(proxy(Block.class, (p, m, a) -> null));
        var expansion = new SmokeExpansion(cells);
        AtomicInteger visits = new AtomicInteger();
        for (int tick = 0; tick < 400; tick++) {
            int age = tick;
            expansion.expand(
                    Math.max(1, tick * 3),
                    block -> {
                        visits.incrementAndGet();
                        return block != cells.getFirst() || age >= 5;
                    });
        }
        assertEquals(185, visits.get());
        assertEquals(180, expansion.cells().size());
    }
}
