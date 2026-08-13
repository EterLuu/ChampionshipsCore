package ink.ziip.championshipscore.configuration;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiLanguageConfigurationTest {
    private static final Pattern GUI_REFERENCE = Pattern.compile(
            "GuiConfig\\.(?:text|lines|component)\\(\\\"([^\\\"]+)\\\"");
    private static final Pattern FORBIDDEN_CHINESE_SPACING = Pattern.compile(
            "(?:[\\p{IsHan}] +(?:%[A-Za-z0-9_.-]+%|\\{\\d+}|[A-Za-z0-9])"
                    + "|(?:%[A-Za-z0-9_.-]+%|\\{\\d+}|[A-Za-z0-9]) +[\\p{IsHan}])");

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
    void guiKeysUseAnEnglishBusinessHierarchyAndEveryLegacyAliasHasATarget() throws IOException {
        YamlConfiguration gui = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        assertEquals(3, gui.getInt("dont-edit-this.version"));

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
        assertTrue(copyKeys.stream().anyMatch(key -> key.startsWith("daily.menus.game-selection.")));
        assertTrue(copyKeys.stream().anyMatch(key -> key.startsWith("spectator.controls.")));

        Properties aliases = new Properties();
        try (var reader = Files.newBufferedReader(
                Path.of("src/main/resources/gui-legacy-aliases.properties"))) {
            aliases.load(reader);
        }
        assertTrue(aliases.size() >= (copyKeys.size() - 2) * 2 - 1,
                "Both version 1 and version 2 GUI keys must migrate to version 3");
        List<String> missingTargets = aliases.values().stream()
                .map(String::valueOf)
                .filter(target -> !gui.isString(target))
                .toList();
        assertTrue(missingTargets.isEmpty(), "Legacy GUI aliases without English targets: " + missingTargets);
    }

    @Test
    void chineseLanguageFilesUseCompactAndConsistentTerminology() {
        for (String resource : List.of("message.yml", "schedule-message.yml", "gui.yml",
                "bingo/lang/zh_CN.yml", "scoreboards.yml")) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(Path.of("src/main/resources", resource).toFile());
            assertNotNull(yaml, resource);
            for (String value : strings(yaml)) {
                String visible = value.replaceAll("&#[0-9A-Fa-f]{6}|&[0-9A-Fa-fK-Ok-oRr]", "");
                assertFalse(FORBIDDEN_CHINESE_SPACING.matcher(visible).find(), resource + ": " + value);
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
