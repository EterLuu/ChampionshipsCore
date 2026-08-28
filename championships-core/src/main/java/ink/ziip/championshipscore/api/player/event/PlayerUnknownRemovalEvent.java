package ink.ziip.championshipscore.api.player.event;

import ink.ziip.championshipscore.api.player.entry.PlayerUnknownRemovalResult;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Requests removal of Core player data whose UUID is absent from the authoritative allowlist.
 * The bridge supplies effective UUIDs explicitly; Core never guesses them from stale usernames.
 */
public final class PlayerUnknownRemovalEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Set<UUID> allowedUuids;
    private final CompletableFuture<PlayerUnknownRemovalResult> completion = new CompletableFuture<>();

    public PlayerUnknownRemovalEvent(@NotNull Set<UUID> allowedUuids) {
        super(true);
        this.allowedUuids = Set.copyOf(allowedUuids);
    }

    @NotNull
    public Set<UUID> getAllowedUuids() {
        return allowedUuids;
    }

    /** Completes after the transaction and all local database-backed caches have been reconciled. */
    @NotNull
    public CompletableFuture<PlayerUnknownRemovalResult> completion() {
        return completion;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
