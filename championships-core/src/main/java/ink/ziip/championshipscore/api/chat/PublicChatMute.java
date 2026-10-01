package ink.ziip.championshipscore.api.chat;

import java.util.Objects;
import java.util.UUID;

/** Absolute expiry ensures a temporary mute keeps elapsing while the player or server is offline. */
public record PublicChatMute(UUID playerId, String playerName, String reason, String actor,
                             long createdAt, long expiresAt) {
    public PublicChatMute {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(actor, "actor");
        if (playerName.isBlank() || createdAt < 0 || expiresAt < 0
                || (expiresAt != 0 && expiresAt <= createdAt))
            throw new IllegalArgumentException("invalid mute record");
    }

    public boolean permanent() { return expiresAt == 0; }

    public boolean activeAt(long now) { return permanent() || now < expiresAt; }
}
