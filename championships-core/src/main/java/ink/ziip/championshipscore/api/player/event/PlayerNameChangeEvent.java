package ink.ziip.championshipscore.api.player.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Emitted by the authentication bridge after an approved Minecraft name change.
 * In offline mode the bridge supplies the deterministic replacement UUID; in
 * online mode the UUID is left null because it is learned from the next login.
 */
public final class PlayerNameChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String oldName;
    private final String newName;
    private final UUID replacementUuid;
    private final CompletableFuture<Boolean> completion = new CompletableFuture<>();

    public PlayerNameChangeEvent(@NotNull String oldName, @NotNull String newName,
                                 @Nullable UUID replacementUuid) {
        super(true);
        this.oldName = oldName;
        this.newName = newName;
        this.replacementUuid = replacementUuid;
    }

    @NotNull
    public String getOldName() {
        return oldName;
    }

    @NotNull
    public String getNewName() {
        return newName;
    }

    @Nullable
    public UUID getReplacementUuid() {
        return replacementUuid;
    }

    /** Completes when Core has committed the corresponding database migration. */
    @NotNull
    public CompletableFuture<Boolean> completion() {
        return completion;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
