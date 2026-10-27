package ink.ziip.championshipscore.presentation.sidebar;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable, validated snapshot of {@code scoreboards.yml}. */
public final class SidebarConfiguration {
    public static final int MAX_LINES = 15;

    private final boolean enabled;
    private final boolean papiFallback;
    private final long updateIntervalTicks;
    private final Template lobby;
    private final Template dailyLobby;
    private final Template mapStatus;
    private final Template mapEdit;
    private final Map<GameTypeEnum, GameTemplate> games;
    private final Map<String, String> values;
    private final MapSidebarPresentation mapPresentation;

    private SidebarConfiguration(
            boolean enabled,
            boolean papiFallback,
            long updateIntervalTicks,
            Template lobby,
            Template dailyLobby,
            Template mapStatus,
            Template mapEdit,
            Map<GameTypeEnum, GameTemplate> games,
            Map<String, String> values,
            MapSidebarPresentation mapPresentation) {
        this.enabled = enabled;
        this.papiFallback = papiFallback;
        this.updateIntervalTicks = updateIntervalTicks;
        this.lobby = lobby;
        this.dailyLobby = dailyLobby;
        this.mapStatus = mapStatus;
        this.mapEdit = mapEdit;
        this.games = Map.copyOf(games);
        this.values = Map.copyOf(values);
        this.mapPresentation = mapPresentation;
    }

    public static @NotNull SidebarConfiguration load(@NotNull File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int version = yaml.getInt("version", -1);
        if (version != 1)
            throw new IllegalArgumentException("unsupported scoreboards.yml version " + version);

        YamlConfiguration defaults = bundledDefaults();
        MapSidebarPresentation mapPresentation = MapSidebarPresentation.load(yaml, defaults);

        Map<String, String> styles = new LinkedHashMap<>();
        ConfigurationSection styleSection = yaml.getConfigurationSection("style");
        if (styleSection != null) {
            for (String key : styleSection.getKeys(false)) {
                styles.put(key.toLowerCase(Locale.ROOT), styleSection.getString(key, ""));
            }
        }

        Map<String, String> values = new LinkedHashMap<>();
        ConfigurationSection valueSection = yaml.getConfigurationSection("values");
        if (valueSection != null) {
            for (String key : valueSection.getKeys(true)) {
                if (valueSection.isConfigurationSection(key)) continue;
                values.put(key, style(valueSection.getString(key, ""), styles));
            }
        }

        Template lobby = template(yaml, "boards.lobby", styles, true);
        if (yaml.isSet("boards.daily-lobby") && !yaml.isConfigurationSection("boards.daily-lobby"))
            throw new IllegalArgumentException("boards.daily-lobby must be a section");
        Template dailyLobby = template(yaml, "boards.daily-lobby", styles, false);
        if (dailyLobby == null) {
            var dailyStyles = new LinkedHashMap<String, String>();
            var defaultStyles = requiredSection(defaults, "style");
            for (String key : defaultStyles.getKeys(false))
                dailyStyles.put(key.toLowerCase(Locale.ROOT), defaultStyles.getString(key, ""));
            dailyStyles.putAll(styles);
            dailyLobby = template(defaults, "boards.daily-lobby", dailyStyles, true);
        }
        Template mapStatus = template(yaml, "boards.map-status", styles, true);
        Template mapEdit = template(yaml, "boards.map-edit", styles, true);

        EnumMap<GameTypeEnum, GameTemplate> games = new EnumMap<>(GameTypeEnum.class);
        ConfigurationSection gamesSection = requiredSection(yaml, "boards.games");
        for (GameTypeEnum gameType : GameTypeEnum.values()) {
            ConfigurationSection gameSection = findSection(gamesSection, gameType.name());
            if (gameSection == null) {
                throw new IllegalArgumentException(
                        "missing game sidebar template " + gameType.name());
            }
            Template base = template(gameSection, styles, true);
            String rankingLine = style(gameSection.getString("ranking-line", ""), styles);
            String ownRankingLine =
                    style(gameSection.getString("own-ranking-line", rankingLine), styles);
            Map<String, Template> mapOverrides = new LinkedHashMap<>();
            ConfigurationSection maps = gameSection.getConfigurationSection("maps");
            if (maps != null) {
                for (String mapName : maps.getKeys(false)) {
                    ConfigurationSection override = maps.getConfigurationSection(mapName);
                    if (override == null) continue;
                    String title = style(override.getString("title", base.title()), styles);
                    List<String> lines =
                            override.contains("lines")
                                    ? override.getStringList("lines").stream()
                                            .map(line -> style(line, styles))
                                            .toList()
                                    : base.lines();
                    validateLines("boards.games." + gameType.name() + ".maps." + mapName, lines);
                    mapOverrides.put(mapName.toLowerCase(Locale.ROOT), new Template(title, lines));
                }
            }
            games.put(
                    gameType,
                    new GameTemplate(base, Map.copyOf(mapOverrides), rankingLine, ownRankingLine));
        }

        long interval = yaml.getLong("settings.update-interval-ticks", 20L);
        if (interval < 1L) throw new IllegalArgumentException("update interval must be positive");
        return new SidebarConfiguration(
                yaml.getBoolean("settings.enabled", true),
                yaml.getBoolean("settings.papi-fallback", true),
                interval,
                lobby,
                dailyLobby,
                mapStatus,
                mapEdit,
                games,
                values,
                mapPresentation);
    }

    private static YamlConfiguration bundledDefaults() {
        try (var stream = SidebarConfiguration.class.getResourceAsStream("/scoreboards.yml")) {
            if (stream == null) throw new IllegalStateException("Missing scoreboards.yml resource");
            var defaults = new YamlConfiguration();
            defaults.load(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
            return defaults;
        } catch (java.io.IOException
                | org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new IllegalStateException("Cannot load sidebar defaults", exception);
        }
    }

    public MapSidebarPresentation mapPresentation() {
        return mapPresentation;
    }

    private static Template template(
            YamlConfiguration yaml, String path, Map<String, String> styles, boolean required) {
        ConfigurationSection section =
                required ? requiredSection(yaml, path) : yaml.getConfigurationSection(path);
        return template(section, styles, required);
    }

    private static Template template(
            ConfigurationSection section, Map<String, String> styles, boolean required) {
        if (section == null) {
            if (required) throw new IllegalArgumentException("missing sidebar section");
            return null;
        }
        String title = style(section.getString("title", ""), styles);
        List<String> lines =
                section.getStringList("lines").stream().map(line -> style(line, styles)).toList();
        if (title.isBlank())
            throw new IllegalArgumentException(
                    "sidebar title must not be blank at " + section.getCurrentPath());
        validateLines(section.getCurrentPath(), lines);
        return new Template(title, lines);
    }

    private static void validateLines(String path, List<String> lines) {
        if (lines.isEmpty())
            throw new IllegalArgumentException("sidebar lines must not be empty at " + path);
        if (lines.size() > MAX_LINES)
            throw new IllegalArgumentException("too many sidebar lines at " + path);
    }

    private static ConfigurationSection requiredSection(YamlConfiguration yaml, String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) throw new IllegalArgumentException("missing sidebar section " + path);
        return section;
    }

    private static ConfigurationSection findSection(ConfigurationSection parent, String expected) {
        for (String key : parent.getKeys(false)) {
            if (key.equalsIgnoreCase(expected)) return parent.getConfigurationSection(key);
        }
        return null;
    }

    private static String style(String input, Map<String, String> styles) {
        String result = input == null ? "" : input;
        for (Map.Entry<String, String> entry : styles.entrySet()) {
            result = result.replace("{style." + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean papiFallback() {
        return papiFallback;
    }

    public long updateIntervalTicks() {
        return updateIntervalTicks;
    }

    public Template lobby() {
        return lobby;
    }

    public Template dailyLobby() {
        return dailyLobby;
    }

    public Template mapStatus() {
        return mapStatus;
    }

    public Template mapEdit() {
        return mapEdit;
    }

    public GameTemplate game(GameTypeEnum gameType) {
        return games.get(gameType);
    }

    public String value(String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }

    /** Frozen worker-facing fields transported inside the existing Bingo presentation map. */
    public Map<String, String> bingoWorkerFields() {
        GameTemplate bingo = games.get(GameTypeEnum.Bingo);
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        result.put("sidebar.title", bingo.base().title());
        result.put("sidebar.ranking-line", bingo.rankingLine());
        result.put("sidebar.own-ranking-line", bingo.ownRankingLine());
        for (int index = 0; index < bingo.base().lines().size(); index++) {
            result.put("sidebar.line." + index, bingo.base().lines().get(index));
        }
        result.put("sidebar.line-count", Integer.toString(bingo.base().lines().size()));
        return Map.copyOf(result);
    }

    public record Template(String title, List<String> lines) {
        public Template {
            lines = List.copyOf(lines);
        }
    }

    public record GameTemplate(
            Template base,
            Map<String, Template> mapOverrides,
            String rankingLine,
            String ownRankingLine) {
        public Template templateFor(String mapName) {
            if (mapName == null) return base;
            return mapOverrides.getOrDefault(mapName.toLowerCase(Locale.ROOT), base);
        }
    }
}
