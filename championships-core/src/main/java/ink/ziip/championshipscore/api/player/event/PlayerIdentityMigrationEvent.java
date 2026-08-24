package ink.ziip.championshipscore.api.player.event;

import ink.ziip.championshipscore.api.player.entry.PlayerUuidMigration;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Requests one atomic Core database migration while the server is in maintenance. */
public final class PlayerIdentityMigrationEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final List<PlayerUuidMigration> players;
    private final CompletableFuture<Integer> completion = new CompletableFuture<>();

    public PlayerIdentityMigrationEvent(@NotNull List<PlayerUuidMigration> players) {
        super(true);
        this.players = List.copyOf(players);
    }

    @NotNull
    public List<PlayerUuidMigration> getPlayers() {
        return players;
    }

    @NotNull
    public CompletableFuture<Integer> completion() {
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
