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
        assertEquals(
                List.of("参与场次：8", "", "3张地图可查看分图明细"), plain(DailyStatsMenu.gameSummaryLore(8, 3)));
    }

    @Test
    void mapCardsDisplayTheirOwnCompletedGameCount() {
        assertEquals(List.of("参与场次：4", "", "按地图独立记录"), plain(DailyStatsMenu.mapSummaryLore(4)));
    }

    @Test
    void playersWithoutRecordsSeeZeroCounts() {
        assertEquals("完整场次：0", plain(DailyStatsMenu.summaryLore(0)).getFirst());
        assertEquals(
                List.of("参与场次：0", "", "0张地图可查看分图明细"), plain(DailyStatsMenu.gameSummaryLore(0, 0)));
        assertEquals("参与场次：0", plain(DailyStatsMenu.mapSummaryLore(0)).getFirst());
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
        assertEquals(List.of("本图 5 场", "地图说明", "额外说明"), plain(DailyStatsMenu.mapSummaryLore(5)));
    }

    @Test
    void customizedGameSummaryReplacesBothCountsOnEitherLine() {
        document.set(
                ITEMS + ".game.lore",
                List.of("%games% 场 / %maps% 张图", "", "已在 %maps% 张图完成 %games% 场"));
        assertEquals(
                List.of("11 场 / 2 张图", "", "已在 2 张图完成 11 场"),
                plain(DailyStatsMenu.gameSummaryLore(11, 2)));
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

    private static List<String> plain(List<Component> lore) {
        return lore.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
    }
}
