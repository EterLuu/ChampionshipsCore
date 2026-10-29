package ink.ziip.championshipscore.api.game.bingo.task.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.api.game.bingo.execution.RemoteBingoContent;
import ink.ziip.championshipscore.api.game.bingo.task.CardDisplayInfo;
import ink.ziip.championshipscore.api.game.bingo.task.EventTask;
import ink.ziip.championshipscore.api.game.bingo.task.OneOfTask;
import ink.ziip.championshipscore.protocol.BingoContentCatalog;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

class DefaultBingoResourcesTest {
    private static final Map<String, Difficulty> EXPECTED_SMITHING_TEMPLATE_DIFFICULTIES =
            Map.ofEntries(
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
        Set<String> expectedMusicDiscs =
                Set.of(
                        "MUSIC_DISC_OTHERSIDE",
                        "MUSIC_DISC_PIGSTEP",
                        "MUSIC_DISC_13",
                        "MUSIC_DISC_CAT",
                        "MUSIC_DISC_BOUNCE",
                        "MUSIC_DISC_CREATOR",
                        "MUSIC_DISC_PRECIPICE",
                        "MUSIC_DISC_CREATOR_MUSIC_BOX",
                        "MUSIC_DISC_TEARS",
                        "MUSIC_DISC_LAVA_CHICKEN",
                        "MUSIC_DISC_RELIC");

        assertEquals(7, sets.stream().filter(entry -> entry.containsKey("all_of")).count());
        assertEquals(130, events.size());
        assertTrue(
                sets.stream()
                        .anyMatch(
                                entry ->
                                        "FURNACE".equals(entry.get("icon"))
                                                && entry.containsKey("all_of")));
        assertTrue(
                events.stream()
                        .anyMatch(
                                entry ->
                                        "craft_unique".equals(entry.get("trigger"))
                                                && Integer.valueOf(50).equals(entry.get("count"))));
        assertTrue(
                events.stream()
                        .anyMatch(
                                entry ->
                                        "kill_family".equals(entry.get("trigger"))
                                                && "UNDEAD".equals(entry.get("param"))
                                                && Integer.valueOf(30).equals(entry.get("count"))));
        assertTrue(
                events.stream()
                        .anyMatch(
                                entry ->
                                        "visit_biomes".equals(entry.get("trigger"))
                                                && "NETHER".equals(entry.get("icon"))
                                                && Integer.valueOf(3).equals(entry.get("count"))));
        assertTrue(
                events.stream()
                        .anyMatch(
                                entry ->
                                        "break_item".equals(entry.get("trigger"))
                                                && "WOODEN_SWORD".equals(entry.get("param"))));
        assertTrue(
                categories.stream()
                        .anyMatch(category -> "sulfur_cave_26_2".equals(category.get("id"))));
        assertTrue(
                categories.stream()
                        .anyMatch(
                                category ->
                                        category.get("members") instanceof List<?> members
                                                && members.stream()
                                                        .anyMatch(
                                                                member ->
                                                                        member
                                                                                        instanceof
                                                                                        Map<?, ?>
                                                                                                entry
                                                                                && "ANCIENT_DEBRIS"
                                                                                        .equals(
                                                                                                entry
                                                                                                        .get(
                                                                                                                "block")))));

        Set<String> individualMusicDiscs =
                categories.stream()
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

        Set<String> smithingTemplates =
                categories.stream()
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

        Map<?, ?> anyMusicDisc =
                sets.stream()
                        .filter(entry -> "MUSIC_DISC_CAT".equals(entry.get("icon")))
                        .findFirst()
                        .orElseThrow();
        assertEquals(List.of("MUSIC_DISC_*"), anyMusicDisc.get("one_of"));
    }

    @Test
    void wildernessBoundCollectionsKeepColoredBedsSeparateAndIncludeTheNewShapes()
            throws Exception {
        try (var ignored = new CollectionItemTypes()) {
            var yaml = resourceYaml("/bingo/cards/default.yml");
            Map<String, String> families =
                    Map.of(
                            "RED_BED", "bed",
                            "WHITE_WOOL_SLAB", "wool_slab",
                            "WHITE_WOOL_STAIRS", "wool_stairs",
                            "WHITE_CUSHION", "cushion",
                            "WHITE_CONCRETE_SLAB", "concrete_slab",
                            "WHITE_CONCRETE_STAIRS", "concrete_stairs");
            Method parse =
                    TaskPoolLoader.class.getDeclaredMethod(
                            "parseOneOf",
                            Map.class,
                            Difficulty.class,
                            Dimension.class,
                            String.class,
                            Logger.class,
                            String.class);
            parse.setAccessible(true);
            for (var family : families.entrySet()) {
                Map<?, ?> row =
                        yaml.getMapList("singletons.sets").stream()
                                .filter(entry -> family.getKey().equals(entry.get("icon")))
                                .findFirst()
                                .orElseThrow();
                @SuppressWarnings("unchecked")
                var entries =
                        (List<PoolEntrySpec>)
                                parse.invoke(
                                        null,
                                        row,
                                        Difficulty.MEDIUM,
                                        Dimension.OVERWORLD,
                                        null,
                                        Logger.getAnonymousLogger(),
                                        "default.yml");
                Set<Material> members =
                        entries.getFirst().members().stream()
                                .map(Material::valueOf)
                                .collect(Collectors.toSet());
                assertEquals(16, members.size(), family.getKey());
                assertFalse(members.contains(Material.STRAW_BED));
                assertEquals(family.getValue(), OneOfTask.familyToken(members));
                OneOfTask task =
                        new OneOfTask(
                                members,
                                Material.valueOf(family.getKey()),
                                null,
                                1,
                                Dimension.OVERWORLD);
                assertEquals(
                        Material.valueOf(
                                "WHITE_" + family.getValue().toUpperCase(java.util.Locale.ROOT)),
                        task.getDisplayMaterial(CardDisplayInfo.DEFAULT));
                for (String locale : List.of("zh_CN", "en_US")) {
                    assertNotNull(
                            resourceYaml("/bingo/lang/" + locale + ".yml")
                                    .getString("task.family." + family.getValue()));
                }
                assertEquals(
                        entries, RemoteBingoContent.filter(new TaskPoolSpec(entries)).entries());
            }
            Map<?, ?> mushrooms =
                    yaml.getMapList("singletons.sets").stream()
                            .filter(row -> "RED_MUSHROOM".equals(row.get("icon")))
                            .findFirst()
                            .orElseThrow();
            assertEquals(
                    List.of("RED_MUSHROOM", "BROWN_MUSHROOM", "SHELF_MUSHROOM"),
                    mushrooms.get("all_of"));
        }
    }

    @Test
    void dappledForestVisitRetainsItsBiomeSubjectAndThemedIcon() throws Exception {
        Map<?, ?> row =
                resourceYaml("/bingo/cards/default.yml").getMapList("singletons.events").stream()
                        .filter(entry -> "DAPPLED_FOREST".equals(entry.get("icon")))
                        .findFirst()
                        .orElseThrow();
        Method parse =
                TaskPoolLoader.class.getDeclaredMethod(
                        "parseEvents", Map.class, Logger.class, String.class);
        parse.setAccessible(true);
        @SuppressWarnings("unchecked")
        var entries =
                (List<PoolEntrySpec>)
                        parse.invoke(null, row, Logger.getAnonymousLogger(), "default.yml");
        assertEquals("event:visit_biomes:DAPPLED_FOREST", entries.getFirst().objectiveId());
        assertEquals(List.of("DAPPLED_FOREST"), entries.getFirst().members());
        assertEquals("dappled_forest_biome", entries.getFirst().category());
        assertEquals(entries, RemoteBingoContent.filter(new TaskPoolSpec(entries)).entries());
        assertEquals(
                Material.POPLAR_SAPLING,
                new EventTask("visit_biomes", "DAPPLED_FOREST", 1, Dimension.OVERWORLD)
                        .getDisplayMaterial(CardDisplayInfo.DEFAULT));
    }

    @Test
    void anyFamiliesIncludeAllDiscsAndPreviouslyMissingVariants() throws Exception {
        try (var ignored = new CollectionItemTypes()) {
            Map<String, Set<String>> required =
                    Map.of(
                            "MUSIC_DISC_CAT",
                                    Arrays.stream(Material.values())
                                            .map(Enum::name)
                                            .filter(name -> name.startsWith("MUSIC_DISC_"))
                                            .collect(Collectors.toSet()),
                            "ORANGE_CANDLE", Set.of("CANDLE", "WHITE_CANDLE", "ORANGE_CANDLE"),
                            "RED_SHULKER_BOX",
                                    Set.of("SHULKER_BOX", "WHITE_SHULKER_BOX", "RED_SHULKER_BOX"),
                            "BIRCH_SAPLING",
                                    Set.of(
                                            "MANGROVE_PROPAGULE",
                                            "AZALEA",
                                            "FLOWERING_AZALEA",
                                            "BIRCH_SAPLING"),
                            "SKELETON_SKULL",
                                    Set.of("DRAGON_HEAD", "SKELETON_SKULL", "PIGLIN_HEAD"),
                            "WOODEN_PICKAXE",
                                    Arrays.stream(Material.values())
                                            .map(Enum::name)
                                            .filter(
                                                    name ->
                                                            !name.startsWith("LEGACY_")
                                                                    && name.endsWith("_PICKAXE"))
                                            .collect(Collectors.toSet()));
            var yaml = resourceYaml("/bingo/cards/default.yml");
            var rows = new java.util.ArrayList<Map<?, ?>>(yaml.getMapList("singletons.sets"));
            for (Map<?, ?> category : yaml.getMapList("categories")) {
                for (Object member : (List<?>) category.get("members")) {
                    if (member instanceof Map<?, ?> row && row.containsKey("one_of")) rows.add(row);
                }
            }
            Method parse =
                    TaskPoolLoader.class.getDeclaredMethod(
                            "parseOneOf",
                            Map.class,
                            Difficulty.class,
                            Dimension.class,
                            String.class,
                            Logger.class,
                            String.class);
            parse.setAccessible(true);
            for (Map<?, ?> row : rows) {
                String icon = (String) row.get("icon");
                if (!required.containsKey(icon)) continue;
                @SuppressWarnings("unchecked")
                var entries =
                        (List<PoolEntrySpec>)
                                parse.invoke(
                                        null,
                                        row,
                                        Difficulty.MEDIUM,
                                        Dimension.OVERWORLD,
                                        null,
                                        Logger.getAnonymousLogger(),
                                        "default.yml");
                PoolEntrySpec entry = entries.getFirst();
                assertEquals("set:" + icon, entry.objectiveId());
                if (required.containsKey(icon)) {
                    assertTrue(entry.members().containsAll(required.get(icon)), icon);
                }
                assertTrue(
                        entry.members().stream().allMatch(name -> Material.valueOf(name).isItem()),
                        icon);
                var remote =
                        RemoteBingoContent.filter(new TaskPoolSpec(entries)).entries().getFirst();
                assertEquals(
                        entry.members().stream()
                                .filter(
                                        name ->
                                                BingoContentCatalog.worker()
                                                        .contains("materials", name))
                                .toList(),
                        remote.members(),
                        icon);
                if (icon.equals("MUSIC_DISC_CAT"))
                    assertEquals(required.get(icon), Set.copyOf(entry.members()));
                if (icon.equals("BIRCH_SAPLING")) {
                    assertEquals(
                            "sapling",
                            OneOfTask.familyToken(
                                    entry.members().stream()
                                            .map(Material::valueOf)
                                            .collect(Collectors.toSet())));
                }
            }
        }
    }

    @Test
    void distinctSaplingCollectionExpandsMixedPatternsAndSpecialNames() throws Exception {
        try (var ignored = new CollectionItemTypes()) {
            var yaml = resourceYaml("/bingo/cards/default.yml");
            Map<?, ?> row =
                    yaml.getMapList("singletons.events").stream()
                            .filter(
                                    entry ->
                                            "unique_collect".equals(entry.get("trigger"))
                                                    && "BIRCH_SAPLING".equals(entry.get("icon")))
                            .findFirst()
                            .orElseThrow();
            Method parse =
                    TaskPoolLoader.class.getDeclaredMethod(
                            "parseEvents", Map.class, Logger.class, String.class);
            parse.setAccessible(true);
            @SuppressWarnings("unchecked")
            var entries =
                    (List<PoolEntrySpec>)
                            parse.invoke(null, row, Logger.getAnonymousLogger(), "default.yml");
            var members = entries.getFirst().members();
            assertTrue(
                    members.containsAll(
                            List.of(
                                    "BIRCH_SAPLING",
                                    "OAK_SAPLING",
                                    "MANGROVE_PROPAGULE",
                                    "AZALEA",
                                    "FLOWERING_AZALEA")));
            assertTrue(
                    members.stream()
                            .noneMatch(name -> name.contains("*") || name.startsWith("POTTED_")));
            assertEquals(4, entries.getFirst().count());
        }
    }

    @Test
    void defaultTierlistRanksMigratedAndLegacyObjectives() throws Exception {
        YamlConfiguration yaml = resourceYaml("/bingo/tierlists/default.yml");
        Method parse =
                TierlistLoader.class.getDeclaredMethod(
                        "parse", YamlConfiguration.class, Logger.class, String.class);
        parse.setAccessible(true);
        Tierlist tierlist =
                (Tierlist)
                        parse.invoke(
                                null,
                                yaml,
                                Logger.getLogger(DefaultBingoResourcesTest.class.getName()),
                                "default.yml");

        assertEquals(Difficulty.EASY, tierlist.resolve("mine:STONE").orElseThrow());
        assertEquals(Difficulty.VERY_HARD, tierlist.resolve("mine:ANCIENT_DEBRIS").orElseThrow());
        assertEquals(Difficulty.HARD, tierlist.resolve("event:craft_unique::50").orElseThrow());
        assertEquals(
                Difficulty.MEDIUM,
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
        assertEquals(
                Difficulty.VERY_HARD,
                tierlist.resolve("MUSIC_DISC_CREATOR_MUSIC_BOX").orElseThrow());
        assertEquals(
                Difficulty.VERY_HARD, tierlist.resolve("MUSIC_DISC_LAVA_CHICKEN").orElseThrow());
        assertEquals(
                Difficulty.MEDIUM,
                tierlist.resolve("set:SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE").orElseThrow());
        EXPECTED_SMITHING_TEMPLATE_DIFFICULTIES.forEach(
                (objectiveId, difficulty) ->
                        assertEquals(
                                difficulty,
                                tierlist.resolve(objectiveId).orElseThrow(),
                                objectiveId));
        assertEquals(
                Difficulty.VERY_HARD, tierlist.resolve("event:wear_full_enchanted:").orElseThrow());
        for (String objective :
                List.of(
                        "WHITE_CUSHION",
                        "LIGHT_BLUE_WOOL_STAIRS",
                        "set:WHITE_WOOL_SLAB",
                        "STRAW_BED",
                        "stat:SLEEP_IN_STRAW_BED")) {
            assertEquals(Difficulty.EASY, tierlist.resolve(objective).orElseThrow(), objective);
        }
        for (String objective :
                List.of(
                        "POPLAR_LOG",
                        "STRIPPED_POPLAR_WOOD",
                        "RED_POPLAR_LEAVES",
                        "SHELF_MUSHROOM",
                        "RED_SHRUB",
                        "GRAY_CONCRETE_STAIRS",
                        "set:WHITE_CONCRETE_SLAB",
                        "event:visit_biomes:DAPPLED_FOREST")) {
            assertEquals(Difficulty.MEDIUM, tierlist.resolve(objective).orElseThrow(), objective);
        }
        for (String objective :
                List.of(
                        "all:RED_MUSHROOM",
                        "ABANDONED_CAMP_MAP",
                        "BURIED_TREASURE_MAP",
                        "set:BURIED_TREASURE_MAP")) {
            assertEquals(Difficulty.ADVANCED, tierlist.resolve(objective).orElseThrow(), objective);
        }
        assertEquals(Difficulty.HARD, tierlist.resolve("BURIED_ANCIENT_CITY_MAP").orElseThrow());
    }

    /**
     * Minimal item/block distinction for these six families; native registries are audited
     * separately.
     */
    private static final class CollectionItemTypes implements AutoCloseable {
        private final java.lang.reflect.Field field;
        private final Map<Material, Object> originals = new java.util.EnumMap<>(Material.class);

        CollectionItemTypes() throws Exception {
            field = Material.class.getDeclaredField("itemType");
            field.setAccessible(true);
            var item =
                    (org.bukkit.inventory.ItemType)
                            java.lang.reflect.Proxy.newProxyInstance(
                                    org.bukkit.inventory.ItemType.class.getClassLoader(),
                                    new Class<?>[] {org.bukkit.inventory.ItemType.class},
                                    (proxy, method, args) -> {
                                        throw new UnsupportedOperationException(method.getName());
                                    });
            for (Material material : Material.values()) {
                String name = material.name();
                if (name.startsWith("LEGACY_")) continue;
                originals.put(material, field.get(material));
                boolean isBlockOnly = name.startsWith("POTTED_") || name.equals("BAMBOO_SAPLING");
                field.set(
                        material,
                        (java.util.function.Supplier<org.bukkit.inventory.ItemType>)
                                () -> isBlockOnly ? null : item);
            }
        }

        @Override
        public void close() throws Exception {
            for (var entry : originals.entrySet()) field.set(entry.getKey(), entry.getValue());
        }
    }

    private static YamlConfiguration resourceYaml(String path) throws Exception {
        try (InputStream input = DefaultBingoResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(input, "Missing test resource " + path);
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
        }
    }
}
