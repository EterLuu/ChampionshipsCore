package ink.ziip.championshipscore.presentation.display;

import org.bukkit.Color;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.Locale;
import java.util.Objects;

/** Immutable appearance only: callers retain entity ownership, positioning and visibility. */
public record TextDisplayStyle(
        Display.Billboard billboard,
        TextDisplay.TextAlignment alignment,
        int lineWidth,
        boolean shadowed,
        boolean seeThrough,
        boolean defaultBackground,
        Color background,
        int textOpacity,
        int blockLight,
        int skyLight) {
    public static final java.util.Set<String> KEYS =
            java.util.Set.of(
                    "billboard",
                    "alignment",
                    "line-width",
                    "shadowed",
                    "see-through",
                    "default-background",
                    "background",
                    "text-opacity",
                    "block-light",
                    "sky-light");

    public TextDisplayStyle {
        Objects.requireNonNull(billboard);
        Objects.requireNonNull(alignment);
        if (lineWidth < 1) throw new IllegalArgumentException("line-width must be positive");
        if (textOpacity < 0 || textOpacity > 255)
            throw new IllegalArgumentException("text-opacity must be between 0 and 255");
        if (blockLight < -1
                || blockLight > 15
                || skyLight < -1
                || skyLight > 15
                || (blockLight == -1) != (skyLight == -1))
            throw new IllegalArgumentException(
                    "block-light and sky-light must both be -1 or each between 0 and 15");
    }

    public void apply(TextDisplay display) {
        display.setBillboard(billboard);
        display.setAlignment(alignment);
        display.setLineWidth(lineWidth);
        display.setShadowed(shadowed);
        display.setSeeThrough(seeThrough);
        display.setDefaultBackground(defaultBackground);
        display.setBackgroundColor(background);
        display.setTextOpacity((byte) textOpacity);
        display.setBrightness(
                blockLight == -1 ? null : new Display.Brightness(blockLight, skyLight));
    }

    public static TextDisplayStyle read(
            YamlConfiguration document, String path, TextDisplayStyle fallback) {
        if (document.isSet(path) && !document.isConfigurationSection(path))
            throw new IllegalArgumentException(path + " must be a section");
        var section = document.getConfigurationSection(path);
        if (section != null)
            for (String key : section.getKeys(false))
                if (!KEYS.contains(key))
                    throw new IllegalArgumentException(path + ": unknown style field " + key);
        try {
            return new TextDisplayStyle(
                    enumeration(
                            document,
                            path,
                            "billboard",
                            Display.Billboard.class,
                            fallback == null ? null : fallback.billboard()),
                    enumeration(
                            document,
                            path,
                            "alignment",
                            TextDisplay.TextAlignment.class,
                            fallback == null ? null : fallback.alignment()),
                    integer(
                            document,
                            path,
                            "line-width",
                            fallback == null ? null : fallback.lineWidth()),
                    flag(document, path, "shadowed", fallback == null ? null : fallback.shadowed()),
                    flag(
                            document,
                            path,
                            "see-through",
                            fallback == null ? null : fallback.seeThrough()),
                    flag(
                            document,
                            path,
                            "default-background",
                            fallback == null ? null : fallback.defaultBackground()),
                    background(document, path, fallback),
                    integer(
                            document,
                            path,
                            "text-opacity",
                            fallback == null ? null : fallback.textOpacity()),
                    integer(
                            document,
                            path,
                            "block-light",
                            fallback == null ? null : fallback.blockLight()),
                    integer(
                            document,
                            path,
                            "sky-light",
                            fallback == null ? null : fallback.skyLight()));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(path + ": " + error.getMessage(), error);
        }
    }

    private static Object value(
            YamlConfiguration document, String path, String key, Object fallback) {
        String field = path + "." + key;
        return document.isSet(field) ? document.get(field) : fallback;
    }

    private static int integer(
            YamlConfiguration document, String path, String key, Integer fallback) {
        Object value = value(document, path, key, fallback);
        if (value instanceof Integer integer) return integer;
        throw new IllegalArgumentException(key + " must be an integer");
    }

    private static boolean flag(
            YamlConfiguration document, String path, String key, Boolean fallback) {
        Object value = value(document, path, key, fallback);
        if (value instanceof Boolean flag) return flag;
        throw new IllegalArgumentException(key + " must be a boolean");
    }

    private static <E extends Enum<E>> E enumeration(
            YamlConfiguration document, String path, String key, Class<E> type, E fallback) {
        Object value = value(document, path, key, fallback == null ? null : fallback.name());
        if (!(value instanceof String text))
            throw new IllegalArgumentException(key + " must be text");
        return Enum.valueOf(type, text.toUpperCase(Locale.ROOT));
    }

    private static Color background(
            YamlConfiguration document, String path, TextDisplayStyle fallback) {
        if (!document.isSet(path + ".background") && fallback != null) return fallback.background();
        Object value = document.get(path + ".background");
        if ("native".equals(value)) return null;
        if (value instanceof String text && text.matches("#[0-9a-fA-F]{8}"))
            return Color.fromARGB((int) Long.parseLong(text.substring(1), 16));
        throw new IllegalArgumentException("background must be native or #AARRGGBB");
    }
}
