package ink.ziip.championshipscore.api.player.entry;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** One player's durable identity change during a server-wide maintenance task. */
public record PlayerUuidMigration(
        @NotNull String username,
        @NotNull UUID fromUuid,
        @NotNull UUID toUuid
) {
}
