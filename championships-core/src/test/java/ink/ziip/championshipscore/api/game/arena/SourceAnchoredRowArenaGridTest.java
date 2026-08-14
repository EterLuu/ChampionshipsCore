package ink.ziip.championshipscore.api.game.arena;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceAnchoredRowArenaGridTest {
    @Test
    void keepsSourceAtZeroAndStartsGeneratedRowAtOne() {
        ArenaGrid grid = new SourceAnchoredRowArenaGrid(
                new Vector(10, 64, 20), new Vector(1024, 64, 20), new Vector(432, 0, 0));

        assertEquals(new Vector(10, 64, 20), grid.origin(0));
        assertEquals(new Vector(1024, 64, 20), grid.origin(1));
        assertEquals(new Vector(1456, 64, 20), grid.origin(2));
        assertEquals(new Vector(1878, 0, 0), grid.delta(3));
    }

    @Test
    void rejectsNegativeCopyIndexes() {
        ArenaGrid grid = new SourceAnchoredRowArenaGrid(
                new Vector(), new Vector(100, 0, 0), new Vector(16, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> grid.origin(-1));
    }
}
