package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.buildmart.presentation.MaterialDisplayFormat;
import ink.ziip.championshipscore.configuration.ConfigSections;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import ink.ziip.championshipscore.presentation.display.TextDisplayStyle;
import ink.ziip.championshipscore.presentation.display.WorldTextStyles;
import ink.ziip.championshipscore.shared.presentation.PlayerTextFormat;

import lombok.Getter;

@Getter
@ConfigSections({
    AceRaceMessages.class,
    AdminMessages.class,
    BattleBoxMessages.class,
    BingoMessages.class,
    BuildMartMessages.class,
    DailyMessages.class,
    DodgeboltMessages.class,
    DragonEggCarnivalMessages.class,
    EventMessages.class,
    FrostbiteMessages.class,
    GameMessages.class,
    HotyCodyDuskyMessages.class,
    LaserBoxMessages.class,
    LaserShowdownMessages.class,
    MapEditorMessages.class,
    ParkourTagMessages.class,
    ParkourWarriorMessages.class,
    RiptideRushMessages.class,
    ScoreMessages.class,
    ServerMessages.class,
    SkyWarsMessages.class,
    SulfurSoccerMessages.class,
    TGTTOSMessages.class,
    TNTRunMessages.class,
    TeamMessages.class
})
public class MessageConfig extends BaseConfigurationFile {
    private static final org.bukkit.configuration.file.YamlConfiguration DEFAULT_DOCUMENT =
            loadDefaults();
    private static final PlayerTextFormat PLAYER_TEXT_DEFAULTS =
            loadPlayerText(DEFAULT_DOCUMENT, null);
    private static final MaterialDisplayFormat MATERIAL_DISPLAY_DEFAULTS =
            MaterialDisplayFormat.read(DEFAULT_DOCUMENT, null);
    private static final java.util.Map<String, String> CODE_DEFAULTS = loadCodeDefaults();

    private record Presentation(
            java.util.Map<String, String> messages,
            PlayerTextFormat playerText,
            MaterialDisplayFormat materials,
            java.util.Map<WorldTextStyles.Profile, TextDisplayStyle> worldText) {}

    private static volatile Presentation presentation =
            new Presentation(
                    CODE_DEFAULTS,
                    PLAYER_TEXT_DEFAULTS,
                    MATERIAL_DISPLAY_DEFAULTS,
                    WorldTextStyles.read(DEFAULT_DOCUMENT, DEFAULT_DOCUMENT));
    private static final java.util.regex.Pattern ARGUMENT =
            java.util.regex.Pattern.compile("%arg([1-9][0-9]*)%");

    private static org.bukkit.configuration.file.YamlConfiguration loadDefaults() {
        try (var stream = MessageConfig.class.getResourceAsStream("/message.yml")) {
            if (stream == null) throw new IllegalStateException("Missing message.yml resource");
            var document = new org.bukkit.configuration.file.YamlConfiguration();
            document.load(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
            return document;
        } catch (java.io.IOException
                | org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new IllegalStateException("Cannot load message.yml text defaults", exception);
        }
    }

    static java.util.List<String> defaultStringList(String path) {
        return DEFAULT_DOCUMENT.getStringList(path);
    }

    private static java.util.Map<String, String> loadCodeDefaults() {
        var section = DEFAULT_DOCUMENT.getConfigurationSection("code-text");
        if (section == null) throw new IllegalStateException("Missing code-text defaults");
        var defaults = new java.util.LinkedHashMap<String, String>();
        section.getValues(true)
                .forEach(
                        (key, value) -> {
                            if (value instanceof String text) defaults.put(key, text);
                        });
        return java.util.Map.copyOf(defaults);
    }

    private static PlayerTextFormat loadPlayerText(
            org.bukkit.configuration.file.YamlConfiguration document, PlayerTextFormat defaults) {
        var values = new java.util.LinkedHashMap<String, String>();
        if (defaults != null) values.putAll(defaults.attributes());
        values.put(PlayerTextFormat.PREFIX + "version", "1");
        for (String key : PlayerTextFormat.KEYS) {
            String path = PlayerTextFormat.PREFIX + key;
            if (defaults != null && !document.isSet(path)) continue;
            Object value = document.get(path);
            if (!(value instanceof String text))
                throw new IllegalArgumentException(path + " must be text");
            values.put(path, text);
        }
        return PlayerTextFormat.from(values);
    }

    public static PlayerTextFormat playerTextFormat() {
        return presentation.playerText();
    }

    public static MaterialDisplayFormat materialDisplayFormat() {
        return presentation.materials();
    }

    public static TextDisplayStyle worldTextStyle(WorldTextStyles.Profile profile) {
        return presentation.worldText().get(profile);
    }

    @Override
    public void loadFromConfiguration(org.bukkit.configuration.file.YamlConfiguration document) {
        var messages = new java.util.LinkedHashMap<>(CODE_DEFAULTS);
        for (String key : CODE_DEFAULTS.keySet()) {
            String path = "code-text." + key;
            if (!document.isSet(path)) continue;
            Object value = document.get(path);
            if (!(value instanceof String text))
                throw new IllegalArgumentException(path + " must be text");
            messages.put(key, text);
        }
        var playerText = loadPlayerText(document, PLAYER_TEXT_DEFAULTS);
        var materialDisplay = MaterialDisplayFormat.read(document, MATERIAL_DISPLAY_DEFAULTS);
        var displayStyles = WorldTextStyles.read(document, DEFAULT_DOCUMENT);
        super.loadFromConfiguration(document);
        presentation =
                new Presentation(
                        java.util.Map.copyOf(messages), playerText, materialDisplay, displayStyles);
    }

    /** Raw templates retain each caller's existing colour conversion and work during bootstrap. */
    public static String codeText(String key, Object... arguments) {
        String template = presentation.messages().get(key);
        if (template == null) throw new IllegalArgumentException("Unknown message key: " + key);
        if (arguments.length == 0) return template;
        var matcher = ARGUMENT.matcher(template);
        var rendered = new StringBuilder();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1)) - 1;
            if (index >= arguments.length)
                throw new IllegalArgumentException("Missing argument for message: " + key);
            String replacement = String.valueOf(arguments[index]);
            // Match Java concatenation even when an object's toString() itself returns null.
            if (replacement == null) replacement = "null";
            matcher.appendReplacement(
                    rendered, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private final String fileName = "message.yml";
    private final String resourceName = "message.yml";

    public MessageConfig(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public int getLatestVersion() {
        return 73;
    }
}
