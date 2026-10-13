package ink.ziip.championshipscore.api.daily.adapter;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.CCConfig;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class LaserShowdownDailyGameAdapterTest {
    @Test
    void configurationCannotAllowSoloMatchesOrRepeatTeamColors() throws Exception {
        var adapter = new LaserShowdownDailyGameAdapter(null);
        CCConfig.DAILY_LASER_SHOWDOWN_MIN_PLAYERS = 1;
        CCConfig.DAILY_LASER_SHOWDOWN_MAX_PLAYERS = 100;
        CCConfig.DAILY_LASER_SHOWDOWN_TEAM_SIZE = 4;
        CCConfig.DAILY_LASER_SHOWDOWN_TEAMS = 100;
        CCConfig.DAILY_LASER_SHOWDOWN_COUNTDOWN_SECONDS = 60;
        assertEquals(GameTypeEnum.LaserShowdown, adapter.game());
        assertEquals(2, adapter.rules().minPlayers());
        assertEquals(16, adapter.rules().maxPlayers());
        assertEquals(16, adapter.rules().teams());
        assertEquals(1, adapter.rules().teamSize());
        CCConfig.DAILY_LASER_SHOWDOWN_TEAMS = 1;
        CCConfig.DAILY_LASER_SHOWDOWN_MAX_PLAYERS = 1;
        assertEquals(2, adapter.rules().teams());
        assertEquals(2, adapter.rules().minPlayers());
        assertNull(adapter.start(List.of()).toCompletableFuture().get());
    }

    @Test
    void defaultsEnableGameAndProvideAllMenuStatesAndStatisticLabels() throws Exception {
        var config =
                YamlConfiguration.loadConfiguration(
                        Path.of("src/main/resources/config.yml").toFile());
        assertTrue(config.getStringList("daily.enabled-games").contains("LaserShowdown"));
        assertEquals(2, config.getInt("daily.games.LaserShowdown.min-players"));
        assertEquals(16, config.getInt("daily.games.LaserShowdown.max-players"));
        assertEquals(16, config.getInt("daily.games.LaserShowdown.teams"));
        var gui =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        String card = "daily.menus.game-selection-screen.items.games.laser-showdown";
        assertNotNull(Material.getMaterial(gui.getString(card + ".material")));
        assertTrue(gui.getString(card + ".title").contains("激光乱斗"));
        for (String state : List.of("available", "waiting", "selected", "selected-waiting")) {
            List<String> lore = gui.getStringList(card + ".states." + state + ".lore");
            assertTrue(lore.stream().anyMatch(line -> line.contains("同场乱斗")));
            assertTrue(lore.stream().anyMatch(line -> line.contains("每队 1 人")));
            assertTrue(lore.stream().anyMatch(line -> line.contains("%queued%")));
        }
        for (String metric :
                List.of(
                        "lasershowdown-wins",
                        "lasershowdown-average-kills",
                        "lasershowdown-headshot-rate",
                        "lasershowdown-favorite-gun"))
            assertFalse(
                    gui.getString(
                                    "daily.menus.statistics-screen.items.metric.states."
                                            + metric
                                            + ".title",
                                    "")
                            .isBlank());
    }
}
