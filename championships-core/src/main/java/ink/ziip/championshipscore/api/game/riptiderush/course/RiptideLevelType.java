package ink.ziip.championshipscore.api.game.riptiderush.course;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Semantic level categories. Visual pass obstacles are generator details, not persisted game rules.
 */
public enum RiptideLevelType {
    MATH("riptide-level-type.math-1", "riptide-level-type.math-2", Material.RED_CONCRETE),
    PASS("riptide-level-type.pass-1", "riptide-level-type.pass-2", Material.IRON_BARS),
    COLOR_FLOOR(
            "riptide-level-type.color-floor-1",
            "riptide-level-type.color-floor-2",
            Material.LIME_CONCRETE),
    DODGE("riptide-level-type.dodge-1", "riptide-level-type.dodge-2", Material.SWEET_BERRIES),
    RHYTHM("riptide-level-type.rhythm-1", "riptide-level-type.rhythm-2", Material.IRON_TRAPDOOR);

    private final String displayName;
    private final String description;
    private final Material icon;

    RiptideLevelType(String displayName, String description, Material icon) {
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
    }

    public @NotNull String displayName() {
        return MessageConfig.codeText(displayName);
    }

    public @NotNull String description() {
        return MessageConfig.codeText(description);
    }

    public @NotNull Material icon() {
        return icon;
    }

    public static @NotNull RiptideLevelType parse(String raw) {
        if (raw == null) throw new IllegalArgumentException("level type is missing");
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
