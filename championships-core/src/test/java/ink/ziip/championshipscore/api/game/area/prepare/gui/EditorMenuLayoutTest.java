package ink.ziip.championshipscore.api.game.area.prepare.gui;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

@ExtendWith(ConfigurationStateExtension.class)
class EditorMenuLayoutTest {
    @Test
    void selectorsShareTheirConfiguredSlotOrderWithClickMapping() throws Exception {
        Map<MenuId, IntFunction<MenuLayout>> selectors =
                Map.of(
                        MenuId.MAP_EDITOR_STEP_LIST, StepMenuGui::layout,
                        MenuId.TGTTOS_AREA_TYPE, TGTTOSAreaTypeGui::layout,
                        MenuId.ACE_RACE_RESPAWN_BINDING, AceRaceRespawnPointBindingGui::layout);
        for (var entry : selectors.entrySet()) {
            var config = new YamlConfiguration();
            config.set(entry.getKey().layout("content"), List.of(14, 1));
            config.set(entry.getKey().item("next") + ".slot", 3);
            config.set(entry.getKey().item("back") + ".slot", 4);
            var field = GuiConfig.class.getDeclaredField("active");
            field.setAccessible(true);
            field.set(null, config);
            var layout = entry.getValue().apply(54);
            assertEquals(3, layout.pageCount(5), entry.getKey().name());
            assertEquals(2, layout.index(1, 14));
            assertEquals(3, layout.index(1, 1));
            assertEquals(-1, layout.index(1, 0));
            assertEquals(3, layout.control("next"));
            assertEquals(4, layout.control("back"));
        }
    }

    @Test
    void listWindowsPreserveLegacySlotsAndAllowExplicitWindowOverrides() throws Exception {
        var defaults =
                YamlConfiguration.loadConfiguration(
                        java.nio.file.Path.of("src/main/resources/gui.yml").toFile());
        var config = new YamlConfiguration();
        config.setDefaults(defaults);
        String family = MenuId.MAP_EDITOR_LIST_EDITOR.path();
        config.set(family + ".items.view.slot", 3);
        config.set(family + ".items.next.slot", 7);
        config.set(MenuId.MAP_EDITOR_LIST_ENTRIES.layout("content"), List.of(20, 3));
        var field = GuiConfig.class.getDeclaredField("active");
        field.setAccessible(true);
        field.set(null, config);
        var manage = ListStepGui.layout(MenuId.MAP_EDITOR_LIST_MANAGE);
        assertEquals(9, manage.size());
        assertEquals(3, manage.control("view"));
        var entries = ListStepGui.layout(MenuId.MAP_EDITOR_LIST_ENTRIES);
        assertEquals(54, entries.size());
        assertEquals(7, entries.control("next"));
        assertEquals(3, entries.pageCount(5));
        assertEquals(3, entries.index(1, 3));
        config.set(MenuId.MAP_EDITOR_LIST_ENTRIES.layout("footer.next"), 8);
        assertEquals(8, ListStepGui.layout(MenuId.MAP_EDITOR_LIST_ENTRIES).control("next"));
        assertEquals(7, entries.control("next"));
        assertEquals(6, ListStepGui.layout(MenuId.MAP_EDITOR_LIST_ENTRY_EDIT).control("delete"));
    }

    @Test
    void buildMartSelectorsUseTheWholeConfiguredPageIncludingSmallIslandLists() throws Exception {
        var defaults =
                YamlConfiguration.loadConfiguration(
                        java.nio.file.Path.of("src/main/resources/gui.yml").toFile());
        var config = new YamlConfiguration();
        config.setDefaults(defaults);
        for (var id : List.of(MenuId.BUILD_MART_BLUEPRINTS, MenuId.BUILD_MART_MATERIAL_ZONES)) {
            config.set(id.layout("content"), List.of(20, 3, 7));
            config.set(id.item("next") + ".slot", 1);
        }
        var field = GuiConfig.class.getDeclaredField("active");
        field.setAccessible(true);
        field.set(null, config);
        var blueprints = BuildMartBlueprintGui.layout();
        assertEquals(45, blueprints.pageCount(135));
        assertEquals(134, blueprints.index(44, 7));
        var islands = BuildMartMaterialZoneGui.layout();
        assertEquals(8, islands.pageCount(24));
        assertEquals(23, islands.index(7, 7));
        assertEquals(-1, islands.index(7, 1));
        assertEquals(1, blueprints.control("next"));
        assertEquals(1, islands.control("next"));
    }
}
