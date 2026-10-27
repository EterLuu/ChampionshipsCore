package ink.ziip.championshipscore.api.game.spectate;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class SpectateMenuTest {
    @Test
    void allSelectorsFreezeSparseCapacityAndControlsWithoutLosingDestinations() throws Exception {
        var document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        var active = GuiConfig.class.getDeclaredField("active");
        active.setAccessible(true);
        active.set(null, document);
        for (var screen :
                List.of(
                        MenuId.SPECTATOR_VENUE_SELECTOR,
                        MenuId.SPECTATOR_SUB_ARENA_SELECTOR,
                        MenuId.SPECTATOR_BUILD_MART_SELECTOR)) {
            document.set(screen.layout("content"), List.of(20, 11, 53));
            document.set(screen.layout("border"), List.of(20, 11, 3));
            document.set(screen.layout("footer.next"), 2);
            var layout = SpectateMenu.layout(screen);
            assertEquals(List.of(20, 11), layout.content());
            assertEquals(List.of(3), layout.border());
            assertEquals(3, layout.pageCount(5));
            assertEquals(List.of(20), layout.pageSlots(2, 5));
            assertEquals(4, layout.index(2, 20, 5));
            document.set(screen.layout("content"), List.of(12));
            document.set(screen.layout("footer.next"), 1);
            assertEquals(2, layout.control("next"));
            assertEquals(1, SpectateMenu.layout(screen).control("next"));
        }
    }

    @Test
    void centeredPartialPagesRouteThroughTheirActualRenderedSlots() throws Exception {
        var document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        var active = GuiConfig.class.getDeclaredField("active");
        active.setAccessible(true);
        active.set(null, document);
        for (var screen :
                List.of(
                        MenuId.SPECTATOR_VENUE_SELECTOR,
                        MenuId.SPECTATOR_SUB_ARENA_SELECTOR,
                        MenuId.SPECTATOR_BUILD_MART_SELECTOR)) {
            document.set(screen.layout("content"), null);
            document.set(screen.layout("content.1"), List.of(22));
            document.set(screen.layout("content.2"), List.of(20, 24));
            var layout = SpectateMenu.layout(screen);
            assertEquals(List.of(22), layout.pageSlots(1, 3));
            assertEquals(2, layout.index(1, 22, 3));
            assertEquals(-1, layout.index(1, 20, 3));
        }
    }
}
