package ink.ziip.championshipscore.platform.bukkit.bingo;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.*;

class BingoEventObjectiveRuleTest {

    @Test
    void objectiveRulesValidateAndDefensivelyCopyCollections() {
        Set<Material> members = new HashSet<>(Set.of(Material.FURNACE));
        BingoEventObjectiveRule rule =
                new BingoEventObjectiveRule("all_collect", null, 1, members, null);
        members.add(Material.SMOKER);

        assertEquals("", rule.param());
        assertEquals(Set.of(Material.FURNACE), rule.members());
        assertEquals(Set.of(), rule.biomeKeys());
        assertThrows(
                IllegalArgumentException.class,
                () -> new BingoEventObjectiveRule(" ", "", 1, Set.of(), Set.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BingoEventObjectiveRule("wear", "", 0, Set.of(), Set.of()));
    }
}
