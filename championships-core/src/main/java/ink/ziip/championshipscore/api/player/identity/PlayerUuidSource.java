package ink.ziip.championshipscore.api.player.identity;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** Selects how Core resolves a player name when no online Player object exists. */
public enum PlayerUuidSource {
    OFFLINE,
    ONLINE;

    public static @NotNull PlayerUuidSource parse(String value) {
        if (value == null || value.isBlank()) return OFFLINE;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("identity.mode must be OFFLINE or ONLINE", exception);
        }
    }
}
