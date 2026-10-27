package ink.ziip.championshipscore.api.daily;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class DailyStatsMenuTest {
    private static final String ITEMS = "daily.menus.statistics-screen.items";
    private YamlConfiguration document;

    @BeforeEach
    void loadGui() throws Exception {
        document = new YamlConfiguration();
        document.load(Path.of("src/main/resources/gui.yml").toFile());
        Field active = GuiConfig.class.getDeclaredField("active");
        active.setAccessible(true);
        active.set(null, document);
    }

    @Test
    void overviewDisplaysTheCompletedGameCount() {
        assertEquals(
                List.of("完整场次：17", "", "按游戏分类，点击卡片查看分图明细"), plain(DailyStatsMenu.summaryLore(17)));
    }

    @Test
    void gameCardsAndDetailSummaryResolveCountsWithoutRenderingTheWholeList() {
        assertEquals(List.of("参与场次：8", "", "3张地图可查看分图明细"), plain(gameLore(8, 3)));
    }

    @Test
    void mapCardsDisplayTheirOwnCompletedGameCount() {
        assertEquals(List.of("参与场次：4", "", "按地图独立记录"), plain(mapLore(4)));
    }

    @Test
    void playersWithoutRecordsSeeZeroCounts() {
        assertEquals("完整场次：0", plain(DailyStatsMenu.summaryLore(0)).getFirst());
        assertEquals(List.of("参与场次：0", "", "0张地图可查看分图明细"), plain(gameLore(0, 0)));
        assertEquals("参与场次：0", plain(mapLore(0)).getFirst());
    }

    @Test
    void customizedOverviewRetainsEveryLineAndItsFormatting() {
        document.set(
                ITEMS + ".summary.lore", List.of("&a完成 %games% 场", "", "说明", "&b另有 %games% 场记录"));
        List<Component> lore = DailyStatsMenu.summaryLore(23);
        assertEquals(List.of("完成 23 场", "", "说明", "另有 23 场记录"), plain(lore));
        assertTrue(LegacyText.serialize(lore.getFirst()).contains("§a"));
        assertTrue(LegacyText.serialize(lore.getLast()).contains("§b"));
    }

    @Test
    void customizedMapLoreUsesTheMapTemplateInsteadOfTheGameTemplate() {
        document.set(ITEMS + ".game.lore", List.of("全游戏 %games% 场", "", "%maps% 张图"));
        document.set(ITEMS + ".map.lore", List.of("本图 %games% 场", "地图说明", "额外说明"));
        assertEquals(List.of("本图 5 场", "地图说明", "额外说明"), plain(mapLore(5)));
    }

    @Test
    void customizedGameSummaryReplacesBothCountsOnEitherLine() {
        document.set(
                ITEMS + ".game-summary.lore",
                List.of("%games% 场 / %maps% 张图", "", "已在 %maps% 张图完成 %games% 场"));
        assertEquals(List.of("11 场 / 2 张图", "", "已在 2 张图完成 11 场"), plain(gameLore(11, 2)));
    }

    @Test
    void everyMetricRendersItsTitleInsteadOfTheConfigurationSection() {
        for (DailyMetric metric : DailyMetric.values()) {
            String key =
                    metric.format() == DailyMetric.Format.TIME
                            ? metric.labelKey(1)
                            : metric.labelKey();
            assertTrue(document.isString(key + ".title"), key);
            assertEquals(
                    document.getString(key + ".title"),
                    DailyStatsMenu.metricRecordLabel(metric, 0),
                    metric.name());
        }
    }

    @Test
    void timedRecordsRenderAllThreeRankedTitles() {
        assertEquals(
                List.of("第一最快单圈", "第二最快单圈", "第三最快单圈"),
                List.of(
                        DailyStatsMenu.metricRecordLabel(DailyMetric.ACERACE_FASTEST_LAP, 0),
                        DailyStatsMenu.metricRecordLabel(DailyMetric.ACERACE_FASTEST_LAP, 1),
                        DailyStatsMenu.metricRecordLabel(DailyMetric.ACERACE_FASTEST_LAP, 2)));
        assertEquals(
                List.of("第一最快完整三圈", "第二最快完整三圈", "第三最快完整三圈"),
                List.of(
                        DailyStatsMenu.metricRecordLabel(DailyMetric.ACERACE_FASTEST_THREE_LAPS, 0),
                        DailyStatsMenu.metricRecordLabel(DailyMetric.ACERACE_FASTEST_THREE_LAPS, 1),
                        DailyStatsMenu.metricRecordLabel(
                                DailyMetric.ACERACE_FASTEST_THREE_LAPS, 2)));
    }

    @Test
    void configuredDefaultPagesKeepCenteredGeometryAndEveryMapReachable() {
        var game =
                new DailyMenuPage(
                        java.util.UUID.randomUUID(),
                        ink.ziip.championshipscore.api.gui.MenuId.DAILY_STATISTICS_OVERVIEW,
                        false,
                        false,
                        0);
        assertEquals(List.of(22), game.layout.pageSlots(0, 1));
        assertEquals(List.of(21, 23), game.layout.pageSlots(0, 2));
        assertEquals(List.of(20, 22, 24), game.layout.pageSlots(0, 3));
        assertEquals(List.of(21, 23, 30, 32), game.layout.pageSlots(0, 4));
        assertEquals(2, game.layout.pageCount(11));
        assertEquals(List.of(22), game.layout.pageSlots(1, 11));
        var maps =
                new DailyMenuPage(
                        java.util.UUID.randomUUID(),
                        ink.ziip.championshipscore.api.gui.MenuId.DAILY_STATISTICS_DETAIL,
                        false,
                        true,
                        2);
        assertEquals(List.of(31), maps.layout.pageSlots(0, 1));
        assertEquals(List.of(30, 31, 32), maps.layout.pageSlots(0, 3));
        assertEquals(List.of(28, 29, 30, 31, 32, 33, 34), maps.layout.pageSlots(0, 7));
        assertEquals(List.of(20, 21, 22, 23, 29, 30, 31, 32), maps.layout.pageSlots(0, 8));
        var visited = new java.util.ArrayList<Integer>();
        for (int page = 0; page < maps.layout.pageCount(47); page++)
            for (int slot : maps.layout.pageSlots(page, 47))
                visited.add(maps.layout.index(page, slot, 47));
        assertEquals(java.util.stream.IntStream.range(0, 47).boxed().toList(), visited);
        assertEquals(2, maps.returnPage);
    }

    @Test
    void customOrderedContentControlsCapacityAndIsFrozenForBothDailyScreens() {
        for (var id :
                List.of(
                        ink.ziip.championshipscore.api.gui.MenuId.DAILY_STATISTICS_OVERVIEW,
                        ink.ziip.championshipscore.api.gui.MenuId.DAILY_STATISTICS_DETAIL,
                        ink.ziip.championshipscore.api.gui.MenuId.DAILY_LEADERBOARD_OVERVIEW,
                        ink.ziip.championshipscore.api.gui.MenuId.DAILY_LEADERBOARD_DETAIL)) {
            document.set(id.path() + ".layout.content", List.of(16, 10));
            var page =
                    new DailyMenuPage(
                            java.util.UUID.randomUUID(),
                            id,
                            id.name().contains("LEADERBOARD"),
                            id.name().endsWith("DETAIL"),
                            0);
            assertEquals(3, page.layout.pageCount(5));
            assertEquals(List.of(16, 10), page.layout.pageSlots(1, 5));
            assertEquals(List.of(16), page.layout.pageSlots(2, 5));
            document.set(id.path() + ".layout.content", List.of(11));
            assertEquals(List.of(16, 10), page.layout.pageSlots(1, 5));
        }
    }

    @Test
    void partialConfigurationInheritsEveryCenteredPlacement() throws Exception {
        var override = new YamlConfiguration();
        override.setDefaults(document);
        override.set(ITEMS + ".previous.slot", 12);
        Field active = GuiConfig.class.getDeclaredField("active");
        active.setAccessible(true);
        active.set(null, override);
        var page =
                new DailyMenuPage(
                        java.util.UUID.randomUUID(),
                        ink.ziip.championshipscore.api.gui.MenuId.DAILY_STATISTICS_DETAIL,
                        false,
                        true,
                        0);
        assertEquals(21, page.layout.content().size());
        assertEquals(List.of(30, 31, 32), page.layout.pageSlots(0, 3));
        assertEquals(12, page.layout.control("previous"));
    }

    @Test
    void recordsMarkerPreservesCustomLinesOrderAndComponentsWithoutReinterpretingValues() {
        var rows = List.of(LegacyText.component("&aA %records%"), LegacyText.component("&bB"));
        var template = List.of("%name%", "%records%", "", "after");
        var expanded =
                DailyRecordItem.expandRecords(
                        template, java.util.Map.of("name", "%records%"), rows);
        assertEquals(List.of("%records%", "A %records%", "B", "", "after"), plain(expanded));
        assertEquals(rows.getFirst(), expanded.get(1));
        assertEquals(
                List.of("before", "", "after"),
                plain(
                        DailyRecordItem.expandRecords(
                                template, java.util.Map.of("name", "before"), List.of())));
    }

    private static List<Component> gameLore(long games, int maps) {
        return DailyRecordItem.lore(
                ITEMS + ".game-summary",
                null,
                java.util.Map.of("games", games, "maps", maps),
                List.of());
    }

    private static List<Component> mapLore(long games) {
        return DailyRecordItem.lore(
                ITEMS + ".map", null, java.util.Map.of("games", games), List.of());
    }

    private static List<String> plain(List<Component> lore) {
        return lore.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
    }
}
