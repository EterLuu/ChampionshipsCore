package ink.ziip.championshipscore.api.team.gui;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class TeamManagementLayoutTest {
    @Test
    void allTeamSelectorsHonorConfiguredCapacityOrderAndMovedNavigation() throws Exception {
        var defaults =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        for (var screen : TeamManagementMenu.Screen.values()) {
            if (screen == TeamManagementMenu.Screen.CONFIRM) continue;
            var config = new YamlConfiguration();
            config.setDefaults(defaults);
            config.set(screen.id.layout("content"), List.of(7, 2, 5));
            config.set(screen.id.layout("border"), List.of());
            config.set(screen.id.item("next") + ".slot", 8);
            activate(config);
            var layout = TeamManagementMenu.layout(screen);
            assertEquals(List.of(7, 2, 5), layout.content(), screen.name());
            assertEquals(List.of(), layout.border());
            assertEquals(8, layout.control("next"));
            assertEquals(14, layout.pageCount(40), screen.name());
            var visited = new ArrayList<Integer>();
            for (int page = 0; page < layout.pageCount(40); page++)
                for (int slot : layout.content()) {
                    int index = layout.index(page, slot);
                    if (index < 40) visited.add(index);
                }
            assertEquals(MenuLayout.range(0, 40), visited, screen.name());
            config.set(screen.id.layout("content"), List.of(12));
            config.set(screen.id.item("next") + ".slot", 9);
            assertEquals(List.of(7, 2, 5), layout.content());
            assertEquals(8, layout.control("next"));
        }
    }

    @Test
    void defaultColorAndTargetTeamLayoutsRetainAllChoicesAndReserveControls() throws Exception {
        activate(
                YamlConfiguration.loadConfiguration(
                        Path.of("src/main/resources/gui.yml").toFile()));
        var colors = TeamManagementMenu.layout(TeamManagementMenu.Screen.COLOR);
        assertEquals(1, colors.pageCount(16));
        assertEquals(
                List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29),
                colors.content().subList(0, 16));
        assertFalse(colors.content().contains(colors.control("back")));
        var teams = TeamManagementMenu.layout(TeamManagementMenu.Screen.TARGET_TEAM);
        assertEquals(3, teams.pageCount(73));
        assertEquals(50, teams.control("next"));
        var confirm = TeamManagementMenu.layout(TeamManagementMenu.Screen.CONFIRM);
        assertEquals(11, confirm.control("confirm"));
        assertEquals(15, confirm.control("cancel"));
    }

    private static void activate(YamlConfiguration document) throws Exception {
        var field = GuiConfig.class.getDeclaredField("active");
        field.setAccessible(true);
        field.set(null, document);
    }
}
