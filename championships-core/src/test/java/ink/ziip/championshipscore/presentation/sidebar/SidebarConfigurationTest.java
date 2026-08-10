package ink.ziip.championshipscore.presentation.sidebar;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidebarConfigurationTest {
    @Test
    void bundledConfigurationCoversEveryGameAndWorkerBingo() throws URISyntaxException {
        File resource = new File(requireResource().toURI());
        SidebarConfiguration configuration = SidebarConfiguration.load(resource);

        assertTrue(configuration.enabled());
        assertEquals(20L, configuration.updateIntervalTicks());
        assertTrue(configuration.lobby().lines().size() <= SidebarConfiguration.MAX_LINES);
        assertFalse(configuration.dailyLobby().lines().isEmpty());
        assertTrue(configuration.dailyLobby().lines().size() <= SidebarConfiguration.MAX_LINES);
        assertTrue(configuration.mapStatus().lines().size() <= SidebarConfiguration.MAX_LINES);
        assertTrue(configuration.mapEdit().lines().size() <= SidebarConfiguration.MAX_LINES);
        for (GameTypeEnum game : GameTypeEnum.values()) {
            SidebarConfiguration.GameTemplate template = configuration.game(game);
            assertNotNull(template, game.name());
            assertFalse(template.base().lines().isEmpty(), game.name());
            assertTrue(template.base().lines().size() <= SidebarConfiguration.MAX_LINES, game.name());
        }

        var worker = configuration.bingoWorkerFields();
        assertTrue(worker.containsKey("sidebar.title"));
        assertTrue(worker.containsKey("sidebar.ranking-line"));
        assertTrue(worker.values().stream().anyMatch("{ranking}"::equals));
        assertEquals(configuration.game(GameTypeEnum.Bingo).base().lines().size(),
                Integer.parseInt(worker.get("sidebar.line-count")));

        var snowball = configuration.game(GameTypeEnum.SnowballShowdown);
        assertTrue(snowball.base().lines().contains("{ranking}"));
        assertTrue(snowball.base().lines().stream().noneMatch(line -> line.contains("snowball_area_rank_")));
    }

    private static java.net.URL requireResource() {
        java.net.URL resource = SidebarConfigurationTest.class.getClassLoader().getResource("scoreboards.yml");
        if (resource == null) throw new AssertionError("missing scoreboards.yml test resource");
        return resource;
    }
}
