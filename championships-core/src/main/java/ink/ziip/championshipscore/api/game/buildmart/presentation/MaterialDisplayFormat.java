package ink.ziip.championshipscore.api.game.buildmart.presentation;

import org.bukkit.configuration.file.YamlConfiguration;

/** One validated snapshot for material labels; the material itself remains translatable. */
public record MaterialDisplayFormat(int columns, String style, String entry, String separator) {
    public static final String PATH = "buildmart.material-display.";

    public MaterialDisplayFormat {
        if (columns < 1) throw new IllegalArgumentException(PATH + "columns must be positive");
        java.util.Objects.requireNonNull(style);
        java.util.Objects.requireNonNull(entry);
        java.util.Objects.requireNonNull(separator);
    }

    public static MaterialDisplayFormat read(
            YamlConfiguration document, MaterialDisplayFormat defaults) {
        Object columns = document.get(PATH + "columns");
        int count;
        if (!document.isSet(PATH + "columns") && defaults != null) count = defaults.columns();
        else if (columns instanceof Integer value) count = value;
        else throw new IllegalArgumentException(PATH + "columns must be an integer");
        return new MaterialDisplayFormat(
                count,
                text(document, "style", defaults == null ? null : defaults.style()),
                text(document, "entry", defaults == null ? null : defaults.entry()),
                text(document, "separator", defaults == null ? null : defaults.separator()));
    }

    private static String text(YamlConfiguration document, String key, String fallback) {
        String path = PATH + key;
        if (!document.isSet(path) && fallback != null) return fallback;
        if (document.get(path) instanceof String value) return value;
        throw new IllegalArgumentException(path + " must be text");
    }
}
