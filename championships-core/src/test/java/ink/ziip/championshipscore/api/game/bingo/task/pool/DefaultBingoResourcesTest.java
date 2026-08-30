package ink.ziip.championshipscore.api.game.bingo.task.pool;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultBingoResourcesTest {
    private static final Map<String, Difficulty> EXPECTED_SMITHING_TEMPLATE_DIFFICULTIES = Map.ofEntries(
            Map.entry("COAST_ARMOR_TRIM_SMITHING_TEMPLATE", Difficulty.MEDIUM),
            Map.entry("DUNE_ARMOR_TRIM_SMITHING_TEMPLATE", Difficulty.ADVANCED),
            Map.entry("SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE", Difficulty.ADVANCED),
            Map.entry("WILD_ARMOR_TRIM_SMITHING_TEMPLATE", Difficulty.ADVANCED),
            Map.entry("BOLT_ARMOR_TRIM_SMITHING_TEMPLATE", Difficulty.HARD),
            Map.entry("FLOW_ARMOR_TRIM_SMITHING_TEMPLATE", Difficulty.HARD),
            Map.entry("HOST_ARMOR_TRIM_SMITHING_TEMPLATE", Difficulty.HARD),
            Map.entry("NETHERITE_UPGRADE_SMITHING_TEMPLATE", Difficulty.HARD),
            Map.entry("RAISER_ARMOR_TRIM_SMITHING_TEMPLATE", Difficulty.HARD),
            Map.entry("RIB_ARMOR_TRIM_SMITHING_TEMPLATE", Difficulty.HARD),
            Map.entry("SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE", Difficulty.HARD),
            Map.entry("SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE", Difficulty.HARD),
            Map.entry("WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE", Difficulty.HARD),
            Map.entry("EYE_ARMOR_TRIM_SMITHING_TEMPLATE", Difficulty.VERY_HARD),
            Map.entry("SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE", Difficulty.VERY_HARD),
            Map.entry("SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE", Difficulty.VERY_HARD),
            Map.entry("TIDE_ARMOR_TRIM_SMITHING_TEMPLATE", Difficulty.VERY_HARD),
            Map.entry("VEX_ARMOR_TRIM_SMITHING_TEMPLATE", Difficulty.VERY_HARD),
            Map.entry("WARD_ARMOR_TRIM_SMITHING_TEMPLATE", Difficulty.VERY_HARD));

    @Test
    void defaultCardContainsEveryMigratedSetAndEvent() throws Exception {
        YamlConfiguration yaml = resourceYaml("/bingo/cards/default.yml");
        var singletons = yaml.getConfigurationSection("singletons");
        assertNotNull(singletons);
        List<Map<?, ?>> sets = singletons.getMapList("sets");
        List<Map<?, ?>> events = singletons.getMapList("events");
        List<Map<?, ?>> categories = yaml.getMapList("categories");
        Set<String> expectedMusicDiscs = Set.of(
                "MUSIC_DISC_OTHERSIDE", "MUSIC_DISC_PIGSTEP", "MUSIC_DISC_13", "MUSIC_DISC_CAT",
                "MUSIC_DISC_BOUNCE", "MUSIC_DISC_CREATOR", "MUSIC_DISC_PRECIPICE",
                "MUSIC_DISC_CREATOR_MUSIC_BOX", "MUSIC_DISC_TEARS", "MUSIC_DISC_LAVA_CHICKEN",
                "MUSIC_DISC_RELIC");

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

        Set<String> individualMusicDiscs = categories.stream()
                .filter(category -> category.get("members") instanceof List<?>)
                .flatMap(category -> ((List<?>) category.get("members")).stream())
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(entry -> entry.get("material"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(material -> material.startsWith("MUSIC_DISC_"))
                .collect(Collectors.toSet());
        assertEquals(expectedMusicDiscs, individualMusicDiscs);

        Set<String> smithingTemplates = categories.stream()
                .filter(category -> category.get("members") instanceof List<?>)
                .flatMap(category -> ((List<?>) category.get("members")).stream())
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(entry -> entry.get("material"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(material -> material.endsWith("SMITHING_TEMPLATE"))
                .collect(Collectors.toSet());
        assertEquals(EXPECTED_SMITHING_TEMPLATE_DIFFICULTIES.keySet(), smithingTemplates);

        Map<?, ?> anyMusicDisc = sets.stream()
                .filter(entry -> "MUSIC_DISC_CAT".equals(entry.get("icon")))
                .findFirst().orElseThrow();
        assertEquals(expectedMusicDiscs, Set.copyOf((List<?>) anyMusicDisc.get("one_of")));
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
        assertEquals(Difficulty.MEDIUM, tierlist.resolve("MUSIC_DISC_13").orElseThrow());
        assertEquals(Difficulty.MEDIUM, tierlist.resolve("MUSIC_DISC_CAT").orElseThrow());
        assertEquals(Difficulty.MEDIUM, tierlist.resolve("set:MUSIC_DISC_CAT").orElseThrow());
        assertEquals(Difficulty.HARD, tierlist.resolve("MUSIC_DISC_OTHERSIDE").orElseThrow());
        assertEquals(Difficulty.HARD, tierlist.resolve("MUSIC_DISC_PIGSTEP").orElseThrow());
        assertEquals(Difficulty.HARD, tierlist.resolve("MUSIC_DISC_PRECIPICE").orElseThrow());
        assertEquals(Difficulty.HARD, tierlist.resolve("MUSIC_DISC_TEARS").orElseThrow());
        assertEquals(Difficulty.HARD, tierlist.resolve("MUSIC_DISC_RELIC").orElseThrow());
        assertEquals(Difficulty.VERY_HARD, tierlist.resolve("MUSIC_DISC_BOUNCE").orElseThrow());
        assertEquals(Difficulty.VERY_HARD, tierlist.resolve("MUSIC_DISC_CREATOR").orElseThrow());
        assertEquals(Difficulty.VERY_HARD,
                tierlist.resolve("MUSIC_DISC_CREATOR_MUSIC_BOX").orElseThrow());
        assertEquals(Difficulty.VERY_HARD,
                tierlist.resolve("MUSIC_DISC_LAVA_CHICKEN").orElseThrow());
        assertEquals(Difficulty.MEDIUM,
                tierlist.resolve("set:SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE").orElseThrow());
        EXPECTED_SMITHING_TEMPLATE_DIFFICULTIES.forEach((objectiveId, difficulty) ->
                assertEquals(difficulty, tierlist.resolve(objectiveId).orElseThrow(), objectiveId));
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
