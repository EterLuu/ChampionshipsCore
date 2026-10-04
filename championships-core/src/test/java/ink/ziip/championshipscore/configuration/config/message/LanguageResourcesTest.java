package ink.ziip.championshipscore.configuration.config.message;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelTemplate;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelType;
import ink.ziip.championshipscore.configuration.ConfigOption;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

class LanguageResourcesTest {
    @Test
    void bundledGuiHasValidStructureAndEveryLiteralTextReference() throws Exception {
        var gui = resource("gui.yml");
        assertEquals(List.of(), GuiConfig.validate(gui));
        var reference =
                Pattern.compile(
                        "GuiConfig\\.(text|component|line|lines)\\(\\s*\"([^\"]+)\"(?=\\s*[,\\)])");
        var errors = new ArrayList<String>();
        try (var sources = Files.walk(Path.of("src/main/java"))) {
            for (var source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                var matcher = reference.matcher(Files.readString(source));
                while (matcher.find()) {
                    String method = matcher.group(1), key = matcher.group(2);
                    boolean list = method.equals("line") || method.equals("lines");
                    if (!(list ? gui.isList(key) : gui.isString(key)))
                        errors.add(
                                source.getFileName()
                                        + ": "
                                        + key
                                        + " must be a "
                                        + (list ? "list" : "string"));
                }
            }
        }
        assertEquals(List.of(), errors);
    }

    @Test
    void messageDocumentsMatchTheirDeclaredConfigurationKeys() {
        for (var entry :
                java.util.Map.of(
                                MessageConfig.class,
                                "message.yml",
                                ScheduleMessageConfig.class,
                                "schedule-message.yml")
                        .entrySet()) {
            var declared = new HashSet<String>();
            for (var field : entry.getKey().getFields()) {
                var option = field.getAnnotation(ConfigOption.class);
                if (option != null) declared.add(option.path());
            }
            var configured = new HashSet<>(leafKeys(resource(entry.getValue())));
            configured.remove("dont-edit-this.version");
            assertEquals(declared, configured, entry.getValue());
        }
    }

    @Test
    void bingoLocalesShareKeysAndContainVoteMenuTitles() {
        var chinese = resource("bingo/lang/zh_CN.yml");
        var english = resource("bingo/lang/en_US.yml");
        assertEquals(leafKeys(chinese), leafKeys(english));
        for (var language : List.of(chinese, english)) {
            for (String key :
                    List.of(
                            "vote.menu_title",
                            "difficulty_vote.menu_title",
                            "lines_vote.menu_title",
                            "genesis.menu_title"))
                assertFalse(language.getString(key, "").isBlank(), key);
        }
    }

    @Test
    void dynamicCourseEditorCategoriesAndVariantsHaveConfiguredPresentation() {
        var gui = resource("gui.yml");
        String editor = "map-editor.games.riptide-rush.menus.course-editor.";
        for (String menu :
                List.of(
                        "category",
                        "entry",
                        "add",
                        "choice",
                        "delete",
                        "course",
                        "preview",
                        "placement",
                        "workshop",
                        "abandon")) assertTrue(gui.isString(editor + menu + ".title"), menu);
        for (String step : List.of("pass", "math", "stopped", "rhythm", "course")) {
            assertTrue(gui.isString(editor + "steps." + step + ".title"), step);
            assertFalse(gui.getStringList(editor + "steps." + step + ".lore").isEmpty(), step);
        }
        for (var type : RiptideLevelType.values()) {
            for (var variant : RiptideLevelTemplate.variants(type)) {
                String option =
                        editor
                                + "items.option-"
                                + variant.toLowerCase(Locale.ROOT).replace('_', '-');
                assertTrue(gui.isString(option + ".title"), option);
                assertTrue(gui.isList(option + ".lore"), option);
                assertTrue(gui.getBoolean(option + ".states.selected.glint"), option);
            }
        }
        for (int difficulty = 1; difficulty <= 3; difficulty++)
            assertTrue(
                    gui.getBoolean(
                            editor + "items.difficulty-" + difficulty + ".states.selected.glint"));
    }

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(Path.of("src/main/resources", name).toFile());
    }

    private static Set<String> leafKeys(YamlConfiguration yaml) {
        return yaml.getKeys(true).stream()
                .filter(key -> !yaml.isConfigurationSection(key))
                .collect(java.util.stream.Collectors.toSet());
    }
}
