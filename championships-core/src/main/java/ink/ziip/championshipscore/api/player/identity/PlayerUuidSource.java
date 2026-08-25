package ink.ziip.championshipscore.api.player.identity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Selects how Core resolves a player name when no online Player object exists. */
public enum PlayerUuidSource {
    OFFLINE,
    PROFILE_UUID;

    public static @NotNull PlayerUuidSource parse(String value) {
        if (value == null || value.isBlank()) return OFFLINE;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("identity.mode must be OFFLINE or PROFILE_UUID", exception);
        }
    }

    /** Validates the configuration required by this source before Core accepts any identity writes. */
    public void validateConfiguration(@Nullable String profileApiBaseUrl) {
        if (this == PROFILE_UUID) ProfileUuidResolver.validateBaseUrl(profileApiBaseUrl);
    }
}
