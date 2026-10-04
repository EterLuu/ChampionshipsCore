package ink.ziip.championshipscore.configuration.config.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ExtendWith(ConfigurationStateExtension.class)
class GuiConfigParsingTest {
    @Test
    void guiPlaceholdersRetainPlayerAndStepFormatting() throws Exception {
        var document = new YamlConfiguration();
        document.set("test.title", "&7<%player%&7> &f%step%");
        activate(document);
        var step = LegacyText.component("&#123abc&l设置出生点");
        var title = GuiConfig.component("test.title", Map.of("player", "&aAlice", "step", step));
        assertEquals("<Alice> 设置出生点", PlainTextComponentSerializer.plainText().serialize(title));
        String encoded = LegacyText.serialize(title);
        assertTrue(encoded.contains("§aAlice"), encoded);
        assertTrue(encoded.contains("§x§1§2§3§a§b§c§l设置出生点"), encoded);
    }

    @Test
    void configuredItalicsRenderWhileUnstyledItemsKeepNormalText() throws Exception {
        var document = new YamlConfiguration();
        document.set("test.italic", "&o说明");
        document.set("test.normal", "说明");
        activate(document);
        assertEquals(
                net.kyori.adventure.text.format.TextDecoration.State.TRUE,
                GuiConfig.component("test.italic")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC));
        assertEquals(
                net.kyori.adventure.text.format.TextDecoration.State.FALSE,
                GuiConfig.component("test.normal")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC));
    }

    @Test
    void laserBoxBallotHasItsNameInEveryState() throws Exception {
        var bundled =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        String path = "voting.menus.ballot.items.games.laserbox";
        assertEquals("CROSSBOW", bundled.getString(path + ".material"));
        // Use the fallback material because Paper's material registry needs a running server.
        bundled.set(path + ".material", null);
        activate(bundled);
        var fallback =
                new GuiConfig.ItemSpec(
                        -1,
                        org.bukkit.Material.CROSSBOW,
                        net.kyori.adventure.text.Component.empty(),
                        List.of(),
                        false);
        var plain = PlainTextComponentSerializer.plainText();
        for (String state : List.of("available", "selected", "leading")) {
            var item =
                    GuiConfig.item(
                            path,
                            state,
                            Map.of("bar", "■■□□□□□□", "votes", 2, "percentage", 25),
                            fallback);
            assertTrue(plain.serialize(item.title()).contains("激光方盒"), state);
            assertEquals(org.bukkit.Material.CROSSBOW, item.material());
            assertFalse(item.lore().isEmpty());
            assertTrue(
                    item.lore().stream()
                            .map(plain::serialize)
                            .anyMatch(line -> line.contains("2票")));
            assertEquals(state.equals("selected"), item.glint());
        }
    }

    @Test
    void everyPrepareStepRendersDescriptionStateAndActionInsteadOfConfigurationKeys()
            throws Exception {
        var document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        activate(document);
        String item = "map-editor.menus.step-list.items.step";
        var plain = PlainTextComponentSerializer.plainText();
        for (StepCaptureType type : StepCaptureType.values()) {
            String actionPath =
                    item
                            + ".actions."
                            + type.name().toLowerCase(Locale.ROOT).replace('_', '-')
                            + ".title";
            assertTrue(document.isString(actionPath), actionPath);
            String action = GuiConfig.text(actionPath);
            var rendered =
                    GuiConfig.lines(
                            item + ".lore",
                            Map.of(
                                    "number",
                                    1,
                                    "title",
                                    "测试步骤",
                                    "description",
                                    "设置目标位置",
                                    "state",
                                    "待设置",
                                    "action",
                                    action));
            assertEquals(
                    List.of("设置目标位置", "待设置", action),
                    rendered.stream().map(LegacyText::component).map(plain::serialize).toList(),
                    type.name());
            assertFalse(action.contains("%"), action);
            assertFalse(action.startsWith("map-editor."), action);
        }
    }

    @Test
    void invalidMenuFieldsReportTheirPaths() {
        var document = new YamlConfiguration();
        document.set("daily.menus.lobby-screen.size", 27);
        document.set("daily.menus.lobby-screen.layout.content", List.of(11, 11, 99));
        document.set("daily.menus.lobby-screen.items.close.slot", 28);
        document.set("daily.menus.lobby-screen.items.close.material", "NOT_A_MATERIAL");
        document.set("daily.menus.lobby-screen.items.close.use", "missing");
        List<String> errors = GuiConfig.validate(document);
        assertEquals(4, errors.size());
        assertTrue(errors.stream().anyMatch(error -> error.contains("layout.content")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("items.close.slot")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("items.close.material")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("items.close.use")));
    }

    @Test
    void menuAndControlReadersKeepSlotsInsideTheInventory() throws Exception {
        var document = new YamlConfiguration();
        document.set("daily.menus.lobby-screen.size", 18);
        document.set("daily.menus.lobby-screen.layout.content", List.of(-1, 11, 11, 90));
        document.set("daily.menus.lobby-screen.items.close.slot", 90);
        activate(document);

        GuiConfig.MenuSpec menu =
                GuiConfig.menu("daily.menus.lobby-screen", 27, "Lobby", List.of(11, 13));
        assertEquals(27, menu.size());
        assertEquals(List.of(11), menu.contentSlots());
        assertEquals(22, ConfiguredGui.slot("daily.menus.lobby-screen.items.close", 22));
        assertFalse(
                GuiConfig.slots("daily.menus.lobby-screen.layout.content", List.of()).contains(90));
    }

    private static void activate(YamlConfiguration document) throws ReflectiveOperationException {
        Field field = GuiConfig.class.getDeclaredField("active");
        field.setAccessible(true);
        field.set(null, document);
    }
}
