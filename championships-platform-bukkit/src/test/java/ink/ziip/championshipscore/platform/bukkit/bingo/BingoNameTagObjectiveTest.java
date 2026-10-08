package ink.ziip.championshipscore.platform.bukkit.bingo;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.*;

class BingoNameTagObjectiveTest {

    @Test
    void nameTagsMatchOnlySupportedEntitiesAndAcceptCaseInsensitiveAliases() {
        record Example(EntityType entity, String name, String expected) {}
        var examples =
                List.of(
                        new Example(EntityType.SHEEP, "jeb_", "SHEEP_JEB"),
                        new Example(EntityType.IRON_GOLEM, "Dinnerbone", "IRON_GOLEM_DINNERBONE"),
                        new Example(EntityType.GHAST, "GRUMM", "GHAST_DINNERBONE"),
                        new Example(EntityType.SHEEP, "Dinnerbone", null),
                        new Example(EntityType.GHAST, "jeb_", null),
                        new Example(EntityType.COW, "jeb_", null));
        for (var example : examples)
            assertEquals(
                    example.expected(),
                    BingoNameTagObjective.match(example.entity(), example.name()),
                    example.toString());
    }
}
