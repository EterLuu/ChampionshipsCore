package ink.ziip.championshipscore.presentation.sidebar;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.shared.presentation.MessageTemplate;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;

/** Immutable map-editor/status presentation, separate from game and worker sidebar templates. */
public record MapSidebarPresentation(
        String entry,
        String runtimeSeparator,
        List<String> gameColors,
        String readyGlyph,
        String publishedGlyph,
        String unpublishedGlyph,
        String progressComplete,
        String progressIncomplete,
        String errorsComplete,
        String errorsIncomplete) {
    public MapSidebarPresentation {
        gameColors = List.copyOf(gameColors);
        if (gameColors.isEmpty())
            throw new IllegalArgumentException(
                    "presentation.map-status.game-colors must not be empty");
    }

    static MapSidebarPresentation load(ConfigurationSection yaml, ConfigurationSection defaults) {
        Object palette = read(yaml, defaults, "map-status.game-colors");
        if (!(palette instanceof List<?> colors)
                || colors.stream().anyMatch(color -> !(color instanceof String)))
            throw new IllegalArgumentException(
                    "presentation.map-status.game-colors must be strings");
        return new MapSidebarPresentation(
                text(yaml, defaults, "map-status.entry"),
                text(yaml, defaults, "map-status.runtime-separator"),
                colors.stream().map(String.class::cast).toList(),
                text(yaml, defaults, "map-status.ready-glyph"),
                text(yaml, defaults, "map-status.published-glyph"),
                text(yaml, defaults, "map-status.unpublished-glyph"),
                text(yaml, defaults, "map-edit.progress-complete"),
                text(yaml, defaults, "map-edit.progress-incomplete"),
                text(yaml, defaults, "map-edit.errors-complete"),
                text(yaml, defaults, "map-edit.errors-incomplete"));
    }

    private static String text(
            ConfigurationSection yaml, ConfigurationSection defaults, String path) {
        Object value = read(yaml, defaults, path);
        if (!(value instanceof String text))
            throw new IllegalArgumentException("presentation." + path + " must be a string");
        return text;
    }

    private static Object read(
            ConfigurationSection yaml, ConfigurationSection defaults, String path) {
        String key = "presentation." + path;
        // Reject an explicit non-section parent rather than treating it as an absent override.
        for (String parent : List.of("presentation", "presentation." + path.split("\\.")[0]))
            if (yaml.isSet(parent) && !yaml.isConfigurationSection(parent))
                throw new IllegalArgumentException(parent + " must be a section");
        return yaml.isSet(key) ? yaml.get(key) : defaults.get(key);
    }

    public String mapEntry(GameTypeEnum game, String mapName, boolean ready, boolean published) {
        return MessageTemplate.render(
                entry,
                Map.of(
                        "{map.game-color}",
                        gameColors.get(game.ordinal() % gameColors.size()),
                        "{map.game}",
                        game.toString(),
                        "{map.publish-glyph}",
                        ready ? readyGlyph : published ? publishedGlyph : unpublishedGlyph,
                        "{map.name}",
                        mapName));
    }

    public String progressColor(boolean complete) {
        return complete ? progressComplete : progressIncomplete;
    }

    public String errorColor(boolean complete) {
        return complete ? errorsComplete : errorsIncomplete;
    }
}
