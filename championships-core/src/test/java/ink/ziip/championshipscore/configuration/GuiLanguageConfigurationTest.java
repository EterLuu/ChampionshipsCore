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
    private static final Pattern GUI_REFERENCE = Pattern.compile("GuiConfig\\.text\\(\\\"([^\\\"]+)\\\"");
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
