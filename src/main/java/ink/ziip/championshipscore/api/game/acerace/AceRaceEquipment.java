package ink.ziip.championshipscore.api.game.acerace;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** Equipment available for the course segment after a progress point. */
public enum AceRaceEquipment {
    NONE("none", "无"),
    ELYTRA("elytra", "鞘翅"),
    TRIDENT("trident", "三叉戟");

    private final String configValue;
    private final String displayName;

    AceRaceEquipment(@NotNull String configValue, @NotNull String displayName) {
        this.configValue = configValue;
        this.displayName = displayName;
    }

    public @NotNull String configValue() {
        return configValue;
    }

    public @NotNull String displayName() {
        return displayName;
    }

    public static @NotNull AceRaceEquipment fromConfig(String value) {
        if (value == null) return NONE;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (AceRaceEquipment equipment : values()) {
            if (equipment.configValue.equals(normalized)) return equipment;
        }
        return NONE;
    }
}
