package ink.ziip.championshipscore.api.game.acerace.mechanics;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** Equipment available for the course segment after a progress point. */
public enum AceRaceEquipment {
    NONE("none", MessageConfig.codeText("ace-race-equipment.none-1")),
    ELYTRA("elytra", MessageConfig.codeText("ace-race-equipment.elytra-1")),
    TRIDENT("trident", MessageConfig.codeText("ace-race-equipment.trident-1")),
    DOLPHINS_GRACE("dolphins_grace", MessageConfig.codeText("ace-race-equipment.dolphins-grace-1"));

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
