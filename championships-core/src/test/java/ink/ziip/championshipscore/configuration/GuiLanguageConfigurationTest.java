package ink.ziip.championshipscore.configuration;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiLanguageConfigurationTest {
    private static final Pattern GUI_REFERENCE = Pattern.compile(
            "GuiConfig\\.(?:text|lines|component)\\(\\\"([^\\\"]+)\\\"");
    private static final Pattern UNPADDED_BULLET = Pattern.compile("(?<=\\S)•|•(?=\\S)");
    private static final Pattern UNPADDED_HASH = Pattern.compile("(?<!：)(?<=\\S)#|#(?=\\S)");
    private static final Pattern SPACE_AFTER_CHINESE_COLON = Pattern.compile("：[ \\t]");
    private static final Pattern UNPADDED_BRACKET_SUFFIX = Pattern.compile("](?=\\S)");

    @Test
    void everyGuiReferenceResolvesToConfiguredCopy() throws IOException {
        YamlConfiguration gui = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        List<String> missing = new ArrayList<>();
        try (var sources = Files.walk(Path.of("src/main/java"))) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = GUI_REFERENCE.matcher(Files.readString(source));
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (!gui.isString(key)) missing.add(source.getFileName() + ":" + key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "Missing gui.yml keys: " + missing);
    }

    @Test
    void guiKeysUseAnEnglishBusinessHierarchy() throws IOException {
        YamlConfiguration gui = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        assertEquals(10, gui.getInt("dont-edit-this.version"));

        List<String> copyKeys = leafKeys(gui).stream()
                .filter(key -> !key.equals("dont-edit-this.version"))
                .toList();
        List<String> nonEnglish = copyKeys.stream()
                .filter(key -> !key.matches("[a-z0-9]+(?:[.-][a-z0-9]+)*"))
                .toList();
        assertTrue(nonEnglish.isEmpty(), "GUI keys must use lowercase English identifiers: " + nonEnglish);

        List<String> implementationShaped = copyKeys.stream()
                .filter(key -> key.matches(".*(?:prepareflow|gui|listener|manager)(?:\\.|$).*$"))
                .toList();
        assertTrue(implementationShaped.isEmpty(),
                "GUI paths must describe product areas, not Java implementation classes: " + implementationShaped);

        List<String> numbered = copyKeys.stream()
                .filter(key -> key.matches("(?:^|.*\\.)(?:text|message|label)-\\d+$"))
                .toList();
        assertTrue(numbered.isEmpty(), "Numbered GUI keys are forbidden: " + numbered);
        assertTrue(copyKeys.stream().anyMatch(key -> key.startsWith("map-editor.games.ace-race.")));
        assertTrue(copyKeys.stream().anyMatch(key -> key.startsWith("daily.menus.game-selection-screen.")));
        assertTrue(copyKeys.stream().anyMatch(key -> key.startsWith("spectator.menus.visibility.")));
    }

    @Test
    void spectatorMenusExposeCompleteFunctionalButtonDefinitions() {
        YamlConfiguration gui = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        ConfigurationSection menus = gui.getConfigurationSection("spectator.menus");
        assertNotNull(menus);
        assertFalse(menus.isConfigurationSection("controls"), "obsolete spectator controls menu must stay removed");
        assertTrue(menus.isConfigurationSection("visibility"));
        assertFalse(gui.contains("spectator.menus.team-position-selector.items.back"));
        assertFalse(gui.contains("spectator.menus.player-visibility-selector.items.back"));
        assertTrue(gui.contains("spectator.menus.visibility.items.show-all"));
        assertTrue(gui.contains("spectator.menus.visibility.items.show-player"));
        assertTrue(gui.contains("spectator.menus.visibility.items.show-team"));
        for (String menuName : menus.getKeys(false)) {
            String menu = "spectator.menus." + menuName;
            assertTrue(gui.isString(menu + ".title"), menu + " needs a title");
            int size = gui.getInt(menu + ".size");
            assertTrue(size >= 9 && size <= 54 && size % 9 == 0, menu + " has invalid size");
            assertFalse(gui.getIntegerList(menu + ".layout.content").isEmpty(),
                    menu + " needs explicit content slots");
            ConfigurationSection items = gui.getConfigurationSection(menu + ".items");
            assertNotNull(items, menu + " needs items");
            for (String itemName : items.getKeys(false)) {
                String item = menu + ".items." + itemName;
                assertTrue(gui.isString(item + ".material"), item + " needs a material");
                assertTrue(gui.isString(item + ".title"), item + " needs a title");
                assertTrue(gui.isList(item + ".lore"), item + " needs lore, even when empty");
                assertTrue(gui.isInt(item + ".slot") || gui.isList(item + ".slots")
                                || isDynamicContentItem(itemName) || "border".equals(itemName),
                        item + " needs a slot or must be a content template");
            }
        }

        ConfigurationSection hotbar = gui.getConfigurationSection("spectator.hotbar");
        assertNotNull(hotbar);
        for (String itemName : hotbar.getKeys(false)) {
            String item = "spectator.hotbar." + itemName;
            assertTrue(gui.isInt(item + ".slot"), item + " needs a slot");
            assertTrue(gui.isString(item + ".material"), item + " needs a material");
            assertTrue(gui.isString(item + ".title"), item + " needs a title");
            assertTrue(gui.isList(item + ".lore"), item + " needs lore, even when empty");
        }

        assertEquals("&#a0a0a0进行中场地：&#55ff55%count%",
                gui.getStringList("spectator.menus.venue-selector.items.status.states.idle.lore").getFirst());
        assertEquals("&#a0a0a0观众：&#55ffff%audience%",
                gui.getStringList("spectator.menus.venue-selector.items.match.lore").get(2));
    }

    @Test
    void primaryPlayerMenusExposeStructuredDefinitions() {
        YamlConfiguration gui = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        for (String menu : List.of(
                "daily.menus.lobby-screen",
                "daily.menus.game-selection-screen",
                "daily.menus.party-screen",
                "daily.menus.leaderboard-screen",
                "daily.menus.statistics-screen",
                "games.bingo.menus.teammate-teleport",
                "games.bingo.menus.card",
                "voting.menus.ballot")) {
            assertTrue(gui.isString(menu + ".title"), menu + " needs a title");
            ConfigurationSection items = gui.getConfigurationSection(menu + ".items");
            assertNotNull(items, menu + " needs functional item definitions");
            assertFalse(items.getKeys(false).isEmpty(), menu + " needs at least one item");
        }
        for (String obsolete : List.of(
                "daily.menus.lobby", "daily.menus.game-selection", "daily.menus.party",
                "daily.menus.leaderboards", "daily.menus.statistics", "map-editor.toolbar")) {
            assertFalse(gui.isConfigurationSection(obsolete), obsolete + " must be represented by a structured menu");
        }
    }

    private static boolean isDynamicContentItem(String itemName) {
        return List.of("match", "destination", "resource-hub", "team-base", "player", "team")
                .contains(itemName);
    }

    @Test
    void languageFilesUseConsistentVisualSeparatorsAndTerminology() {
        for (String resource : List.of("message.yml", "schedule-message.yml", "gui.yml",
                "bingo/lang/zh_CN.yml", "scoreboards.yml")) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(Path.of("src/main/resources", resource).toFile());
            assertNotNull(yaml, resource);
            for (String value : strings(yaml)) {
                String visible = value.replaceAll("&?#[0-9A-Fa-f]{6}|&[0-9A-Fa-fK-Ok-oRr]", "");
                assertFalse(visible.contains("·"), resource + ": " + value);
                assertFalse(UNPADDED_BULLET.matcher(visible).find(), resource + ": " + value);
                assertFalse(UNPADDED_HASH.matcher(visible).find(), resource + ": " + value);
                assertFalse(SPACE_AFTER_CHINESE_COLON.matcher(visible).find(), resource + ": " + value);
                String withoutPlaceholders = visible.replaceAll("%[^%]*%", "");
                assertFalse(UNPADDED_BRACKET_SUFFIX.matcher(withoutPlaceholders).find(), resource + ": " + value);
                assertFalse(visible.contains("建材集市"), resource + ": " + value);
                assertFalse(visible.contains("TNT 雨"), resource + ": " + value);
                assertFalse(visible.contains("Bingo"), resource + ": " + value);
                assertFalse(visible.contains("AceRace"), resource + ": " + value);
                if (resource.equals("gui.yml")) {
                    for (String stiff : List.of("旁观玩家", "PrepareSpot", "copy0", "revision",
                            "实际信息", "盖章", "实例容量", "空闲实例", "同行小队", "同游者")) {
                        assertFalse(visible.contains(stiff), resource + " contains stiff GUI copy: " + value);
                    }
                }
            }
        }
    }

    @Test
    void bingoLocalesExposeTheSameKeys() {
        YamlConfiguration chinese = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/bingo/lang/zh_CN.yml").toFile());
        YamlConfiguration english = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/bingo/lang/en_US.yml").toFile());
        assertEquals(leafKeys(chinese), leafKeys(english));
    }

    private static List<String> strings(ConfigurationSection section) {
        List<String> values = new ArrayList<>();
        for (String key : section.getKeys(true)) {
            Object value = section.get(key);
            if (value instanceof String text) values.add(text);
            if (value instanceof List<?> list) list.stream().filter(String.class::isInstance)
                    .map(String.class::cast).forEach(values::add);
        }
        return values;
    }

    private static List<String> leafKeys(ConfigurationSection section) {
        return section.getKeys(true).stream()
                .filter(key -> !section.isConfigurationSection(key))
                .sorted()
                .toList();
    }
}
