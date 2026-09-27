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

        assertFalse(gui.isConfigurationSection("common"), "top-level common GUI state is obsolete");
        assertFalse(gui.isConfigurationSection("buttons.copy"), "shared buttons must not reintroduce copy");
        List<String> copyKeys = leafKeys(gui).stream()
                .filter(key -> !key.equals("dont-edit-this.version"))
                .toList();
        assertFalse(copyKeys.stream().anyMatch(key -> key.contains(".copy.")),
                "copy keys are obsolete: " + copyKeys.stream().filter(key -> key.contains(".copy.")).toList());
        assertFalse(gui.isConfigurationSection("map-editor.steps"), "map-editor steps must live under a menu");
        assertFalse(gui.isConfigurationSection("map-editor.session"), "map-editor session text must live under step-list");
        assertFalse(gui.isConfigurationSection("teams.menus.shared"), "teams shared text must bind to buttons or move to messages");
        ConfigurationSection mapEditorGames = gui.getConfigurationSection("map-editor.games");
        List<String> obsoleteGameFlows = new ArrayList<>();
        if (mapEditorGames != null) {
            for (String game : mapEditorGames.getKeys(false)) {
                for (String section : List.of("setup", "steps", "session")) {
                    if (gui.isConfigurationSection("map-editor.games." + game + "." + section)) {
                        obsoleteGameFlows.add("map-editor.games." + game + "." + section);
                    }
                }
            }
        }
        assertTrue(obsoleteGameFlows.isEmpty(),
                "game flow text must live under map-editor menus: " + obsoleteGameFlows);
        List<String> nonEnglish = copyKeys.stream()
                .filter(key -> !key.matches("[a-z0-9]+(?:[.-][a-z0-9]+)*"))
                .toList();
        assertTrue(nonEnglish.isEmpty(), "GUI keys must use lowercase English identifiers: " + nonEnglish);

        List<String> implementationShaped = copyKeys.stream()
                .filter(key -> key.matches(".*(?:prepareflow|gui|listener|manager)(?:\\.|$).*$"))
                .toList();
        assertTrue(implementationShaped.isEmpty(),
                "GUI paths must describe product areas, not Java implementation classes: " + implementationShaped);

        assertFalse(gui.isConfigurationSection("daily.metrics"), "daily metrics are metric button states, not a free text group");
        List<String> textSections = copyKeys.stream()
                .filter(key -> key.equals("daily.metrics")
                        || key.startsWith("daily.metrics.")
                        || key.endsWith(".text")
                        || key.contains(".text."))
                .toList();
        assertTrue(textSections.isEmpty(), "loose GUI text sections are forbidden: " + textSections);

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
        assertTrue(menus.isConfigurationSection("player-teleport-selector"));
        assertFalse(menus.isConfigurationSection("team-position-selector"));
        assertFalse(gui.contains("spectator.menus.player-visibility-selector.items.back"));
        assertFalse(gui.contains("spectator.hotbar.tracking"));
        assertFalse(gui.contains("spectator.hotbar.team-position"));
        assertEquals("COMPASS", gui.getString("spectator.hotbar.player-teleport.material"));
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

    @Test
    void mapEditorStepItemsExposeDynamicTitleAndLore() {
        YamlConfiguration gui = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/gui.yml").toFile());
        String step = "map-editor.menus.step-list.items.step";
        for (String placeholder : List.of("%number%", "%title%"))
            assertTrue(gui.getString(step + ".title", "").contains(placeholder), placeholder);
        for (String placeholder : List.of("%description%", "%state%", "%action%"))
            assertTrue(gui.getStringList(step + ".lore").stream().anyMatch(line -> line.contains(placeholder)), placeholder);
        assertTrue(gui.getString(step + ".states.set-list.title", "").contains("%count%"));
    }

    private static boolean isDynamicContentItem(String itemName) {
        return List.of("match", "destination", "resource-hub", "team-base", "player", "team")
                .contains(itemName);
    }

    @Test
    void bingoLocalesExposeTheSameKeys() {
        YamlConfiguration chinese = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/bingo/lang/zh_CN.yml").toFile());
        YamlConfiguration english = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/bingo/lang/en_US.yml").toFile());
        assertEquals(leafKeys(chinese), leafKeys(english));
    }

    @Test
    void bingoLocalesContainEveryVoteMenuTitle() {
        for (String locale : List.of("zh_CN", "en_US")) {
            YamlConfiguration language = YamlConfiguration.loadConfiguration(
                    Path.of("src/main/resources/bingo/lang/" + locale + ".yml").toFile());
            for (String key : List.of("vote.menu_title", "difficulty_vote.menu_title",
                    "lines_vote.menu_title", "genesis.menu_title")) {
                assertFalse(language.getString(key, "").isBlank(), locale + " is missing " + key);
            }
        }
    }

    @Test
    void everyPresentationLeafIsBoundToAMenuOrButton() {
        YamlConfiguration gui = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        List<String> structuralSuffixes = List.of(".material", ".slot", ".slots", ".use", ".glint");
        List<String> invalid = new ArrayList<>();
        for (String key : leafKeys(gui)) {
            if (key.equals("dont-edit-this.version")
                    || key.endsWith(".layout.content") || key.endsWith(".layout.border")
                    || key.endsWith(".size")
                    || structuralSuffixes.stream().anyMatch(key::endsWith)) {
                continue;
            }
            if (!key.endsWith(".title") && !key.endsWith(".lore")) invalid.add(key);
        }
        assertTrue(invalid.isEmpty(), "GUI presentation must only use title/lore leaves: " + invalid);

        List<String> unbound = new ArrayList<>();
        for (String key : leafKeys(gui)) {
            if (key.equals("dont-edit-this.version") || !key.endsWith(".title") && !key.endsWith(".lore")) continue;
            if (!key.startsWith("buttons.") && !key.contains(".buttons.")
                    && !key.contains(".menus.") && !key.contains(".hotbar."))
                unbound.add(key);
        }
        assertTrue(unbound.isEmpty(), "GUI copy must be bound to a menu, button or hotbar item: " + unbound);

        List<String> badStateLeaves = leafKeys(gui).stream()
                .filter(key -> key.contains(".states."))
                .filter(key -> {
                    String suffix = key.substring(key.lastIndexOf('.') + 1);
                    return !suffix.equals("title") && !suffix.equals("lore") && !suffix.equals("material")
                            && !suffix.equals("glint") && !suffix.equals("slot") && !suffix.equals("slots");
                })
                .toList();
        assertTrue(badStateLeaves.isEmpty(), "button states may override only presentation fields: " + badStateLeaves);
    }

    private static List<String> leafKeys(ConfigurationSection section) {
        return section.getKeys(true).stream()
                .filter(key -> !section.isConfigurationSection(key))
                .sorted()
                .toList();
    }
}
