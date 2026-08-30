package ink.ziip.championshipscore.platform.bukkit.bingo;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BingoNameTagObjectiveTest {
    @Test
    void recognizesSupportedSpecialNamesCaseInsensitively() {
        assertEquals("SHEEP_JEB", BingoNameTagObjective.match(EntityType.SHEEP, "jeb_"));
        assertEquals("IRON_GOLEM_DINNERBONE",
                BingoNameTagObjective.match(EntityType.IRON_GOLEM, "Dinnerbone"));
        assertEquals("GHAST_DINNERBONE", BingoNameTagObjective.match(EntityType.GHAST, "GRUMM"));
    }

    @Test
    void rejectsUnsupportedEntityAndNameCombinations() {
        assertNull(BingoNameTagObjective.match(EntityType.SHEEP, "Dinnerbone"));
        assertNull(BingoNameTagObjective.match(EntityType.GHAST, "jeb_"));
        assertNull(BingoNameTagObjective.match(EntityType.COW, "jeb_"));
    }
}
