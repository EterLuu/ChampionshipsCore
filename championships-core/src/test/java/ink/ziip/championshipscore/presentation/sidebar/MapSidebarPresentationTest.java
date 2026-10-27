package ink.ziip.championshipscore.presentation.sidebar;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

class MapSidebarPresentationTest {
    @TempDir Path directory;

    @org.junit.jupiter.api.BeforeEach
    void loadGameNames() throws Exception {
        ink.ziip.championshipscore.support.MessageConfigurations.defaults()
                .loadFromConfiguration(new YamlConfiguration());
    }

    private YamlConfiguration defaults() throws Exception {
        var result = new YamlConfiguration();
        try (var stream = getClass().getResourceAsStream("/scoreboards.yml")) {
            assertNotNull(stream);
            result.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return result;
    }

    private SidebarConfiguration load(YamlConfiguration document) throws Exception {
        var file = directory.resolve("scoreboards.yml").toFile();
        document.save(file);
        return SidebarConfiguration.load(file);
    }

    @Test
    void bundledRowsPreserveEveryGameAndPublishState() throws Exception {
        var text = load(defaults()).mapPresentation();
        var colors = List.of("#ff7373", "#64b5f6", "#81c784", "#ba68c8", "#ffb74d", "#4dd0e1");
        for (GameTypeEnum game : GameTypeEnum.values()) {
            String prefix = "  " + colors.get(game.ordinal() % 6) + game + " &8/ ";
            assertEquals(prefix + "&a✓ &f地图", text.mapEntry(game, "地图", true, true));
            assertEquals(prefix + "&e⚠ &f地图", text.mapEntry(game, "地图", false, true));
            assertEquals(prefix + "&c✗ &f地图", text.mapEntry(game, "地图", false, false));
        }
        assertEquals("&7 / ", text.runtimeSeparator());
        assertEquals("&a", text.progressColor(true));
        assertEquals("&e", text.progressColor(false));
        assertEquals("&a", text.errorColor(true));
        assertEquals("&c", text.errorColor(false));
    }

    @Test
    void olderConfigurationInheritsPresentationWithoutChangingAnyBoardsOrWorkerFields()
            throws Exception {
        var document = defaults();
        document.set("style.title", "&dCustom");
        document.set("values.game.spectator", "Custom spectator");
        var full = load(document);
        document.set("presentation", null);
        var old = load(document);
        assertEquals(full.mapPresentation(), old.mapPresentation());
        assertEquals(full.lobby(), old.lobby());
        assertEquals(full.dailyLobby(), old.dailyLobby());
        assertEquals(full.mapEdit(), old.mapEdit());
        assertEquals(full.mapStatus(), old.mapStatus());
        assertEquals(full.bingoWorkerFields(), old.bingoWorkerFields());
        for (GameTypeEnum game : GameTypeEnum.values())
            assertEquals(full.game(game), old.game(game));
        assertEquals("Custom spectator", old.value("game.spectator", "fallback"));
    }

    @Test
    void partialOverridesEmptyValuesAndRemovedOverridesUseTheCurrentSnapshot() throws Exception {
        var document = defaults();
        var original = load(document).mapPresentation();
        document.set("presentation", null);
        document.set("presentation.map-status.ready-glyph", "");
        document.set("presentation.map-status.runtime-separator", "");
        document.set("presentation.map-edit.progress-incomplete", "#123456");
        var custom = load(document).mapPresentation();
        assertEquals("", custom.readyGlyph());
        assertEquals("", custom.runtimeSeparator());
        assertEquals("#123456", custom.progressColor(false));
        assertEquals(original.errorsIncomplete(), custom.errorsIncomplete());
        document.set("presentation", null);
        assertEquals(original, load(document).mapPresentation());
        assertEquals("#123456", custom.progressColor(false));
    }

    @Test
    void completeCustomTemplateDoesNotReinterpretMapNamesOrConfiguredGlyphs() throws Exception {
        var document = defaults();
        document.set(
                "presentation.map-status.entry",
                "{map.name}|{map.publish-glyph}|{map.game-color}|{map.game}");
        document.set("presentation.map-status.ready-glyph", "{map.name}");
        document.set("presentation.map-status.game-colors", List.of("&1", "&2"));
        var text = load(document).mapPresentation();
        for (GameTypeEnum game : GameTypeEnum.values())
            assertEquals(
                    "{map.game}|{map.name}|" + (game.ordinal() % 2 == 0 ? "&1" : "&2") + "|" + game,
                    text.mapEntry(game, "{map.game}", true, true));
        assertThrows(UnsupportedOperationException.class, () -> text.gameColors().add("&3"));
    }

    @Test
    void malformedOverridesFailWithoutReplacingThePreviousSnapshot() throws Exception {
        var previous = load(defaults()).mapPresentation();
        for (String path :
                List.of(
                        "presentation",
                        "presentation.map-status",
                        "presentation.map-edit",
                        "presentation.map-status.entry",
                        "presentation.map-status.runtime-separator",
                        "presentation.map-status.ready-glyph",
                        "presentation.map-status.published-glyph",
                        "presentation.map-status.unpublished-glyph",
                        "presentation.map-edit.progress-complete",
                        "presentation.map-edit.progress-incomplete",
                        "presentation.map-edit.errors-complete",
                        "presentation.map-edit.errors-incomplete")) {
            var invalid = defaults();
            invalid.set(path, 42);
            assertThrows(IllegalArgumentException.class, () -> load(invalid), path);
        }
        for (Object invalidPalette : List.of("red", List.of(), List.of("&a", 42))) {
            var invalid = defaults();
            invalid.set("presentation.map-status.game-colors", invalidPalette);
            assertThrows(IllegalArgumentException.class, () -> load(invalid));
        }
        assertEquals(previous, load(defaults()).mapPresentation());
    }
}
