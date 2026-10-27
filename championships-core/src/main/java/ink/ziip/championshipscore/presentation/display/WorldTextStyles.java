package ink.ziip.championshipscore.presentation.display;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.EnumMap;
import java.util.Map;

/** Configured profiles for the four existing world text owners, with shared appearance defaults. */
public final class WorldTextStyles {
    public static final String PATH = "presentation.world-text";

    public enum Profile {
        ACE_RACE_NAME("ace-race-name"),
        BUILD_MART_MATERIALS("build-mart-materials"),
        RIPTIDE_INSTRUCTION("riptide-instruction"),
        RIPTIDE_COLOR_TARGET("riptide-color-target");

        private final String key;

        Profile(String key) {
            this.key = key;
        }

        public String path() {
            return PATH + ".profiles." + key;
        }
    }

    private WorldTextStyles() {}

    public static Map<Profile, TextDisplayStyle> read(
            YamlConfiguration document, YamlConfiguration bundled) {
        for (String root : new String[] {PATH, PATH + ".profiles"})
            if (document.isSet(root) && !document.isConfigurationSection(root))
                throw new IllegalArgumentException(root + " must be a section");
        validateKeys(document, PATH, java.util.Set.of("defaults", "profiles"));
        validateKeys(
                document,
                PATH + ".profiles",
                java.util.Arrays.stream(Profile.values())
                        .map(profile -> profile.key)
                        .collect(java.util.stream.Collectors.toSet()));
        String basePath = PATH + ".defaults";
        var base =
                TextDisplayStyle.read(
                        document, basePath, TextDisplayStyle.read(bundled, basePath, null));
        var result = new EnumMap<Profile, TextDisplayStyle>(Profile.class);
        for (Profile profile : Profile.values()) {
            var defaults = TextDisplayStyle.read(bundled, profile.path(), base);
            result.put(profile, TextDisplayStyle.read(document, profile.path(), defaults));
        }
        return Map.copyOf(result);
    }

    private static void validateKeys(
            YamlConfiguration document, String path, java.util.Set<String> keys) {
        var section = document.getConfigurationSection(path);
        if (section == null) return;
        for (String key : section.getKeys(false))
            if (!keys.contains(key))
                throw new IllegalArgumentException(path + ": unknown key " + key);
    }
}
