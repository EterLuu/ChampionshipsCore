package ink.ziip.championshipscore.api.player.identity;

import org.jetbrains.annotations.NotNull;

/** A stable failure category for administrator-facing offline identity lookups. */
public final class PlayerUuidLookupException extends Exception {
    private final Reason reason;

    public PlayerUuidLookupException(@NotNull Reason reason, @NotNull String message) {
        super(message);
        this.reason = reason;
    }

    public PlayerUuidLookupException(@NotNull Reason reason, @NotNull String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public @NotNull Reason reason() {
        return reason;
    }

    public enum Reason {
        INVALID_USERNAME,
        PLAYER_NOT_FOUND,
        SERVICE_UNAVAILABLE,
        INVALID_RESPONSE,
        CONFIGURATION,
        IDENTITY_CONFLICT
    }
}
