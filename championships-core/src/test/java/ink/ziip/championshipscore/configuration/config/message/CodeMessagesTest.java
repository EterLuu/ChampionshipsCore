package ink.ziip.championshipscore.configuration.config.message;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelType;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;

@ExtendWith(ConfigurationStateExtension.class)
class CodeMessagesTest {
    @Test
    void bossBarDurationTemplatesFollowReloadsAndDeletionRestoresBundledUnits() throws Exception {
        var config = config();
        var document = defaults();
        config.loadFromConfiguration(document);
        assertEquals("60秒", CoreMessages.bossBarTime(60));
        assertEquals("1分1秒", CoreMessages.bossBarTime(61));
        document.set("code-text.duration.seconds", "%seconds%s");
        document.set("code-text.duration.minutes-seconds", "%minutes%m %seconds%s");
        config.loadFromConfiguration(document);
        assertEquals("60s", CoreMessages.bossBarTime(60));
        assertEquals("1m 1s", CoreMessages.bossBarTime(61));
        document.set("code-text.duration", null);
        config.loadFromConfiguration(document);
        assertEquals("1分1秒", CoreMessages.bossBarTime(61));
    }

    private static final String KEY = "core-messages.format-admin-info-1";

    private static MessageConfig config() throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var plugin =
                (ChampionshipsCore)
                        ((sun.misc.Unsafe) unsafeField.get(null))
                                .allocateInstance(ChampionshipsCore.class);
        return new MessageConfig(plugin);
    }

    private static YamlConfiguration defaults() {
        return YamlConfiguration.loadConfiguration(
                new InputStreamReader(
                        CodeMessagesTest.class.getResourceAsStream("/message.yml"),
                        StandardCharsets.UTF_8));
    }

    @Test
    void everyProductionTextReferenceExistsAndSourceContainsNoHanCharacters() throws Exception {
        var defaults = defaults();
        var reference = Pattern.compile("MessageConfig\\.codeText\\(\\s*\"([^\"]+)\"");
        var errors = new ArrayList<String>();
        try (var sources = Files.walk(Path.of("src/main/java"))) {
            for (var source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(source);
                if (text.codePoints()
                        .anyMatch(
                                c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN))
                    errors.add(source + " contains Han characters");
                var matcher = reference.matcher(text);
                while (matcher.find())
                    if (!defaults.isString("code-text." + matcher.group(1)))
                        errors.add(source + " has missing message: " + matcher.group(1));
            }
        }
        assertEquals(java.util.List.of(), errors);
    }

    @Test
    void defaultsAreAvailableBeforePluginBootstrapAndPreserveRawColours() {
        assertEquals(defaults().getString("code-text." + KEY), MessageConfig.codeText(KEY));
        assertEquals(
                defaults().getString("code-text." + KEY).replace("%arg1%", "hello"),
                MessageConfig.codeText(KEY, "hello"));
    }

    @Test
    void reloadAppliesOverridesAndRemovingThemRestoresBundledDefaults() throws Exception {
        var config = config();
        var document = defaults();
        // Trigger enum initialization before replacing its labels.
        RiptideLevelType.MATH.displayName();
        document.set("code-text." + KEY, "[%arg2%] %arg1% / %arg2%");
        document.set("code-text.riptide-level-type.math-1", "custom label");
        config.loadFromConfiguration(document);
        assertEquals("[second] first / second", MessageConfig.codeText(KEY, "first", "second"));
        assertEquals("custom label", RiptideLevelType.MATH.displayName());
        document.set("code-text", null);
        config.loadFromConfiguration(document);
        assertEquals(defaults().getString("code-text." + KEY), MessageConfig.codeText(KEY));
        assertEquals(
                defaults().getString("code-text.riptide-level-type.math-1"),
                RiptideLevelType.MATH.displayName());
    }

    @Test
    void argumentsAreInsertedOnceWithoutInterpretingPlaceholderLikePlayerText() throws Exception {
        var config = config();
        var document = defaults();
        document.set("code-text." + KEY, "%arg2% %arg1% %arg2%");
        config.loadFromConfiguration(document);
        assertEquals("$1\\ %arg2% $1\\", MessageConfig.codeText(KEY, "%arg2%", "$1\\"));
        assertThrows(IllegalArgumentException.class, () -> MessageConfig.codeText(KEY, "only one"));
    }

    @Test
    void explicitEmptyTextIsPreservedAndMalformedReloadDoesNotPublishChanges() throws Exception {
        var config = config();
        var document = defaults();
        document.set("code-text." + KEY, "");
        config.loadFromConfiguration(document);
        assertEquals("", MessageConfig.codeText(KEY));
        document.set("code-text." + KEY, 123);
        assertThrows(IllegalArgumentException.class, () -> config.loadFromConfiguration(document));
        assertEquals("", MessageConfig.codeText(KEY));
        assertThrows(IllegalArgumentException.class, () -> MessageConfig.codeText("missing.key"));
    }

    @Test
    void nullArgumentsRetainJavaConcatenationBehaviour() throws Exception {
        var document = defaults();
        document.set("code-text." + KEY, "[%arg1%]");
        config().loadFromConfiguration(document);
        assertEquals("[null]", MessageConfig.codeText(KEY, (Object) null));
        Object noLabel =
                new Object() {
                    @Override
                    public String toString() {
                        return null;
                    }
                };
        assertEquals("[null]", MessageConfig.codeText(KEY, noLabel));
    }
}
