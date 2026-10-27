package ink.ziip.championshipscore.presentation.sidebar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.util.List;

class SidebarConfigurationTest {
    @Test
    void missingDailyBoardUsesBundledContentWithRuntimeStyles(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var document =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new File(requireResource().toURI()));
        document.set("style.title", "&aCustom title");
        document.set("style.footer", "Custom footer");
        var file = directory.resolve("scoreboards.yml").toFile();
        document.save(file);
        var control = SidebarConfiguration.load(file);
        document.set("boards.daily-lobby", null);
        document.save(file);
        var result = SidebarConfiguration.load(file);
        var bundled = SidebarConfiguration.load(new File(requireResource().toURI()));
        assertEquals("&aCustom title", result.dailyLobby().title());
        assertEquals("Custom footer", result.dailyLobby().lines().getLast());
        assertEquals(
                bundled.dailyLobby().lines().subList(0, bundled.dailyLobby().lines().size() - 1),
                result.dailyLobby().lines().subList(0, result.dailyLobby().lines().size() - 1));
        assertEquals(control.bingoWorkerFields(), result.bingoWorkerFields());
        document.set("style", null);
        document.save(file);
        assertEquals(bundled.dailyLobby(), SidebarConfiguration.load(file).dailyLobby());
    }

    @Test
    void explicitDailyBoardIsNotReplacedByDefaults(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var document =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new File(requireResource().toURI()));
        document.set("boards.daily-lobby.title", "Custom");
        document.set("boards.daily-lobby.lines", List.of("", "own line"));
        var file = directory.resolve("scoreboards.yml").toFile();
        document.save(file);
        assertEquals(List.of("", "own line"), SidebarConfiguration.load(file).dailyLobby().lines());
        document.set("boards.daily-lobby.title", "");
        document.save(file);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> SidebarConfiguration.load(file));
        document.set("boards.daily-lobby", "invalid section");
        document.save(file);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> SidebarConfiguration.load(file));
    }

    @Test
    void selectsTopEightAndAppendsOutOfRangeViewerLikeBingo() {
        List<Integer> ranked = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        assertEquals(
                List.of(1, 2, 3, 4, 5, 6, 7, 8, 10),
                CoreSidebarManager.selectRankingRows(ranked, 10));
        assertEquals(
                List.of(1, 2, 3, 4, 5, 6, 7, 8), CoreSidebarManager.selectRankingRows(ranked, 3));
    }

    @Test
    void bundledConfigurationCoversEveryGameAndWorkerBingo() throws URISyntaxException {
        File resource = new File(requireResource().toURI());
        SidebarConfiguration configuration = SidebarConfiguration.load(resource);

        assertTrue(configuration.enabled());
        assertEquals(20L, configuration.updateIntervalTicks());
        assertTrue(configuration.lobby().lines().size() <= SidebarConfiguration.MAX_LINES);
        assertFalse(configuration.dailyLobby().lines().isEmpty());
        assertTrue(configuration.dailyLobby().lines().size() <= SidebarConfiguration.MAX_LINES);
        assertEquals(configuration.lobby().title(), configuration.dailyLobby().title());
        assertEquals(
                configuration.lobby().lines().get(0), configuration.dailyLobby().lines().get(0));
        assertTrue(
                configuration.dailyLobby().lines().stream()
                        .noneMatch(
                                line ->
                                        line.contains("自由游玩")
                                                || line.contains("游玩场次")
                                                || line.contains("胜场")
                                                || line.contains("/cc play")));
        assertTrue(configuration.mapStatus().lines().size() <= SidebarConfiguration.MAX_LINES);
        assertTrue(configuration.mapEdit().lines().size() <= SidebarConfiguration.MAX_LINES);
        for (GameTypeEnum game : GameTypeEnum.values()) {
            SidebarConfiguration.GameTemplate template = configuration.game(game);
            assertNotNull(template, game.name());
            assertFalse(template.base().lines().isEmpty(), game.name());
            assertTrue(
                    template.base().lines().size() <= SidebarConfiguration.MAX_LINES, game.name());
        }

        var worker = configuration.bingoWorkerFields();
        assertTrue(worker.containsKey("sidebar.title"));
        assertTrue(worker.containsKey("sidebar.ranking-line"));
        assertTrue(worker.values().stream().anyMatch("{ranking}"::equals));
        assertEquals(
                configuration.game(GameTypeEnum.Bingo).base().lines().size(),
                Integer.parseInt(worker.get("sidebar.line-count")));

        var lasershowdown = configuration.game(GameTypeEnum.LaserShowdown);
        assertTrue(lasershowdown.base().lines().contains("{ranking}"));
        assertTrue(
                lasershowdown.base().lines().stream()
                        .noneMatch(line -> line.contains("lasershowdown_area_rank_")));
    }

    @Test
    void focusedGameSidebarsUseOnlyTheRequestedLiveFields() throws URISyntaxException {
        SidebarConfiguration configuration =
                SidebarConfiguration.load(new File(requireResource().toURI()));

        var riptide = configuration.game(GameTypeEnum.RiptideRush).base().lines();
        assertTrue(riptide.stream().anyMatch(line -> line.contains("{riptide.progress}")));
        assertTrue(riptide.stream().anyMatch(line -> line.contains("{riptide.alive}")));
        assertTrue(riptide.stream().anyMatch(line -> line.contains("{riptide.challenge}")));
        assertFalse(
                riptide.stream().anyMatch(line -> line.contains("剩余时间") || line.contains("timer")));

        var frostbite = configuration.game(GameTypeEnum.FrostbiteFrenzy).base().lines();
        assertTrue(frostbite.stream().anyMatch(line -> line.contains("{frostbite.kills}")));
        assertTrue(frostbite.stream().anyMatch(line -> line.contains("{frostbite.state}")));
        assertFalse(
                frostbite.stream()
                        .anyMatch(
                                line ->
                                        line.contains("当前道具")
                                                || line.contains("当前场地")
                                                || line.contains("剩余时间")
                                                || line.contains("timer")));

        var laserBox = configuration.game(GameTypeEnum.LaserBox).base().lines();
        assertTrue(laserBox.stream().anyMatch(line -> line.contains("{laserbox.kills}")));
        assertFalse(
                laserBox.stream()
                        .anyMatch(
                                line ->
                                        line.contains("护盾")
                                                || line.contains("重生倒计时")
                                                || line.contains("剩余时间")
                                                || line.contains("timer")));
    }

    private static java.net.URL requireResource() {
        java.net.URL resource =
                SidebarConfigurationTest.class.getClassLoader().getResource("scoreboards.yml");
        if (resource == null) throw new AssertionError("missing scoreboards.yml test resource");
        return resource;
    }
}
