package ink.ziip.championshipscore.protocol;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Map;

class BingoContentCatalogTest {
    private final BingoContentCatalog catalog = BingoContentCatalog.worker26_2();

    @Test
    void retainsExistingTasksAcrossRegistryKinds() {
        assertTrue(supports("item", Map.of("material", "minecraft:red_bed")));
        assertTrue(supports("statistic", Map.of("statistic", "SLEEP_IN_BED")));
        assertTrue(supports("advancement", Map.of("key", "story/mine_stone")));
        assertTrue(supports("potion", Map.of("effect", "strength")));
        assertTrue(supports("event", Map.of("trigger", "tame", "param", "WOLF")));
        assertTrue(supports("event", Map.of("trigger", "wear", "param", "IRON")));
        assertTrue(supports("event", Map.of("trigger", "drink", "param", "WATER_BOTTLE")));
        assertTrue(supports("event", Map.of("trigger", "die", "param", "FIREWORK")));
        assertTrue(
                supports(
                        "event",
                        Map.of("trigger", "kill_family", "subjects", "ENTITY_TYPE=UNDEAD")));
    }

    @Test
    void rejectsNewMaterialsStatisticsBiomesAndAdvancements() {
        assertFalse(supports("item", Map.of("material", "STRAW_BED")));
        assertFalse(supports("item", Map.of("material", "POPLAR_PLANKS")));
        assertFalse(supports("statistic", Map.of("statistic", "SLEEP_IN_STRAW_BED")));
        assertFalse(
                supports(
                        "event",
                        Map.of("trigger", "visit_biomes", "subjects", "BIOME=DAPPLED_FOREST")));
        assertFalse(supports("advancement", Map.of("key", "minecraft:adventure/future_objective")));
    }

    @Test
    void checksEveryMemberAndPresentationReference() {
        for (String type : new String[] {"item_set", "all_of"}) {
            assertTrue(supports(type, Map.of("materials", "RED_BED,BLUE_BED")));
            assertFalse(supports(type, Map.of("materials", "RED_BED,STRAW_BED")));
        }
        assertFalse(supports("item", Map.of("material", "DIRT", "display.material", "STRAW_BED")));
        assertFalse(supports("event", Map.of("trigger", "eat", "param", "STRAW_BED")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        catalog.requireSupported(
                                new BingoTaskSpec(
                                        0,
                                        "new-statistic",
                                        "statistic",
                                        Map.of("statistic", "SLEEP_IN_STRAW_BED"))));
    }

    private boolean supports(String type, Map<String, String> attributes) {
        return catalog.supports(new BingoTaskSpec(0, "test", type, attributes));
    }
}
