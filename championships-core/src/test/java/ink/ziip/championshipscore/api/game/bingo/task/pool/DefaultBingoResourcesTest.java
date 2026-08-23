package ink.ziip.championshipscore.api.game.bingo.task.pool;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultBingoResourcesTest {
    @Test
    void defaultCardContainsEveryMigratedSetAndEvent() throws Exception {
        YamlConfiguration yaml = resourceYaml("/bingo/cards/default.yml");
        var singletons = yaml.getConfigurationSection("singletons");
        assertNotNull(singletons);
        List<Map<?, ?>> sets = singletons.getMapList("sets");
        List<Map<?, ?>> events = singletons.getMapList("events");
        List<Map<?, ?>> categories = yaml.getMapList("categories");

        assertEquals(7, sets.stream().filter(entry -> entry.containsKey("all_of")).count());
        assertEquals(129, events.size());
        assertTrue(sets.stream().anyMatch(entry -> "FURNACE".equals(entry.get("icon"))
                && entry.containsKey("all_of")));
        assertTrue(events.stream().anyMatch(entry -> "craft_unique".equals(entry.get("trigger"))
                && Integer.valueOf(50).equals(entry.get("count"))));
        assertTrue(events.stream().anyMatch(entry -> "kill_family".equals(entry.get("trigger"))
                && "UNDEAD".equals(entry.get("param")) && Integer.valueOf(30).equals(entry.get("count"))));
        assertTrue(events.stream().anyMatch(entry -> "visit_biomes".equals(entry.get("trigger"))
                && "NETHER".equals(entry.get("icon")) && Integer.valueOf(3).equals(entry.get("count"))));
        assertTrue(events.stream().anyMatch(entry -> "break_item".equals(entry.get("trigger"))
                && "WOODEN_SWORD".equals(entry.get("param"))));
        assertTrue(categories.stream().anyMatch(category -> "sulfur_cave_26_2".equals(category.get("id"))));
        assertTrue(categories.stream().anyMatch(category -> category.get("members") instanceof List<?> members
                && members.stream().anyMatch(member -> member instanceof Map<?, ?> entry
                && "ANCIENT_DEBRIS".equals(entry.get("block")))));
    }

    @Test
    void defaultTierlistRanksMigratedAndLegacyObjectives() throws Exception {
        YamlConfiguration yaml = resourceYaml("/bingo/tierlists/default.yml");
        Method parse = TierlistLoader.class.getDeclaredMethod(
                "parse", YamlConfiguration.class, Logger.class, String.class);
        parse.setAccessible(true);
        Tierlist tierlist = (Tierlist) parse.invoke(null, yaml,
                Logger.getLogger(DefaultBingoResourcesTest.class.getName()), "default.yml");

        assertEquals(Difficulty.EASY, tierlist.resolve("mine:STONE").orElseThrow());
        assertEquals(Difficulty.VERY_HARD, tierlist.resolve("mine:ANCIENT_DEBRIS").orElseThrow());
        assertEquals(Difficulty.HARD, tierlist.resolve("event:craft_unique::50").orElseThrow());
        assertEquals(Difficulty.MEDIUM,
                tierlist.resolve("event:break_item:WOODEN_PICKAXE").orElseThrow());
        assertEquals(Difficulty.VERY_HARD, tierlist.resolve("MUSIC_DISC_BOUNCE").orElseThrow());
        assertEquals(Difficulty.VERY_HARD,
                tierlist.resolve("event:wear_full_enchanted:").orElseThrow());
    }

    private static YamlConfiguration resourceYaml(String path) throws Exception {
        try (InputStream input = DefaultBingoResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(input, "Missing test resource " + path);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
    }
}
