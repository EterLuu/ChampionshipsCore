package ink.ziip.championshipscore.api.team.gui;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.platform.bukkit.scoreboard.TeamColors;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.support.TestObjects;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

@ExtendWith(ConfigurationStateExtension.class)
class TeamMenuItemsTest {
    private static YamlConfiguration document() throws Exception {
        var doc =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        TestObjects.field(GuiConfig.class, "active").set(null, doc);
        return doc;
    }

    @Test
    void everyTeamListVisitsAllEntriesWithSparseAndCenteredPageArrangements() throws Exception {
        var doc = document();
        for (var screen : TeamManagementMenu.Screen.values()) {
            if (screen == TeamManagementMenu.Screen.CONFIRM) continue;
            String content = screen.id.layout("content");
            doc.set(content, null);
            doc.set(content + ".1", List.of(22));
            doc.set(content + ".2", List.of(20, 24));
            doc.set(content + ".3", List.of(12, 13, 14));
            var layout = TeamManagementMenu.layout(screen);
            assertTrue(MenuId.supportsContentArrangements(screen.id.path()));
            assertEquals(3, layout.pageCount(8));
            assertEquals(List.of(20, 24), layout.pageSlots(2, 8));
            assertEquals(7, layout.index(2, 24, 8));
            assertEquals(-1, layout.index(2, 14, 8));
            doc.set(content, List.of(18, 17));
            assertEquals(4, TeamManagementMenu.layout(screen).pageCount(8));
            assertEquals(List.of(20, 24), layout.pageSlots(2, 8));
        }
    }

    @Test
    void persistentColourAliasesSelectTheCompleteStateAndRetainLiteralLabels() throws Exception {
        var doc = document();
        for (String color : TeamColors.names()) {
            String state = TeamMenuItems.colorState(color);
            String path = MenuId.TEAMS_COLOR_PICKER.item("color") + ".states." + state;
            assertTrue(doc.isString(path + ".title"), color);
            doc.set(path + ".title", "&aLiteral %name% " + color);
            doc.set(path + ".lore", List.of("before", "%availability%", "after"));
            doc.set(path + ".material", "DIAMOND");
            doc.set(path + ".glint", true);
            assertEquals(
                    "Literal %name% " + color,
                    LegacyText.plainText(
                            LegacyText.serialize(
                                    TeamMenuItems.colorLabel(color.toUpperCase(Locale.ROOT)))));
            var item = GuiConfig.itemTemplate(MenuId.TEAMS_COLOR_PICKER.item("color"), state);
            assertEquals("DIAMOND", item.material());
            assertTrue(item.glint());
            assertEquals(3, item.lore().size());
        }
        assertEquals("yellow-green", TeamMenuItems.colorState("lime"));
        assertEquals("light-blue", TeamMenuItems.colorState("light_blue"));
        assertEquals("light-gray", TeamMenuItems.colorState("light_gray"));
    }

    @Test
    void nativeDyePaletteRetainsAllCreationColoursIndependentlyOfDefaultLocale() {
        String[] expected = {
            "#F9FFFE", "#F9801D", "#C74EBD", "#3AB3DA", "#FED83D", "#80C71F", "#F38BAA", "#474F52",
            "#9D9D97", "#169C9C", "#8932B8", "#3C44AA", "#835432", "#5E7C16", "#B02E26", "#1D1D21"
        };
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            String[] colors = TeamColors.names();
            for (int i = 0; i < colors.length; i++)
                assertEquals(expected[i], TeamMenuItems.colorHex(colors[i]));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
