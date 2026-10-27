package ink.ziip.championshipscore.api.daily;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.gui.GuiItemLore;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ExtendWith(ConfigurationStateExtension.class)
class DailyLobbyLayoutTest {
    private YamlConfiguration document;

    @BeforeEach
    void loadGui() throws Exception {
        document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        Field active = GuiConfig.class.getDeclaredField("active");
        active.setAccessible(true);
        active.set(null, document);
    }

    private DailyMenuPage holder(Class<? extends DailyMenuPage> type) throws Exception {
        var constructor = type.getDeclaredConstructor(UUID.class);
        constructor.setAccessible(true);
        return constructor.newInstance(UUID.randomUUID());
    }

    @Test
    void smallConfiguredPagesRetainEveryEntryAndTheirOriginalNavigationAfterReload()
            throws Exception {
        for (var type : List.of(DailyGameMenu.MenuHolder.class, DailyPartyMenu.PartyHolder.class)) {
            String path =
                    "daily.menus."
                            + (type == DailyGameMenu.MenuHolder.class
                                    ? "game-selection-screen"
                                    : "party-screen");
            document.set(path + ".layout.content", List.of(16, 10));
            document.set(path + ".layout.footer.next", 12);
            var window = holder(type);
            assertEquals(3, window.layout.pageCount(5));
            assertEquals(List.of(16, 10), window.layout.pageSlots(0, 5));
            window.paginate(5);
            assertTrue(window.navigate(12));
            window.paginate(5);
            document.set(path + ".layout.footer.next", 13);
            document.set(path + ".layout.content", List.of(11));
            assertFalse(window.navigate(13));
            assertTrue(window.navigate(12));
            assertEquals(List.of(16), window.layout.pageSlots(window.page, 5));
            window.paginate(1);
            assertEquals(0, window.page);
            assertEquals(1, window.pageCount);
            assertEquals(13, holder(type).layout.control("next"));
        }
    }

    @Test
    void emptyOverlaysDoNotConsumeContentAndCannotCoverFixedControls() throws Exception {
        for (var type : List.of(DailyGameMenu.MenuHolder.class, DailyPartyMenu.PartyHolder.class)) {
            var window = holder(type);
            assertTrue(window.layout.content().contains(window.emptySlot));
        }
    }

    @Test
    void lobbyControlsAndBordersAreCapturedTogether() throws Exception {
        var window = holder(DailyLobbyMenu.LobbyHolder.class);
        document.set("daily.menus.lobby-screen.items.close.slot", 23);
        assertEquals(22, window.layout.control("close"));
        assertEquals(23, holder(DailyLobbyMenu.LobbyHolder.class).layout.control("close"));
    }

    @Test
    void memberRowsHaveAnExplicitPositionAndParameterTextDoesNotCreateRows() {
        var lore =
                GuiItemLore.expand(
                        List.of("Before %leader%", "%members%", "After", "Extra"),
                        Map.of("leader", "%members%"),
                        "%members%",
                        List.of(Component.text("Member")));
        assertEquals(
                List.of("Before %members%", "Member", "After", "Extra"),
                lore.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList());
        assertEquals(
                List.of(),
                GuiItemLore.expand(
                        List.of(), Map.of(), "%members%", List.of(Component.text("hidden"))));
    }
}
