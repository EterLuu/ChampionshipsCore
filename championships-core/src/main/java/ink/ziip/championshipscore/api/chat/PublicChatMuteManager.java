package ink.ziip.championshipscore.api.chat;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Keeps profile lookups and mute-file writes off the server and asynchronous chat threads. */
public final class PublicChatMuteManager extends BaseManager {
    private final PublicChatMuteStore store;
    private final ExecutorService writes;

    public PublicChatMuteManager(ChampionshipsCore plugin) {
        super(plugin);
        store = new PublicChatMuteStore(plugin.getDataFolder().toPath().resolve("mutes.yml"), Clock.systemUTC());
        writes = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "ChampionshipsCore-Mutes");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override public void load() {
        try { store.load(); }
        catch (IOException error) { throw new IllegalStateException("Cannot load public chat mutes", error); }
    }

    @Override public void unload() {
        writes.shutdown();
        try {
            if (!writes.awaitTermination(5, TimeUnit.SECONDS))
                plugin.getLogger().warning("Public chat mute writes are still finishing during shutdown");
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }

    public PublicChatMute activeMute(UUID id) { return store.activeMute(id); }

    public List<String> mutedNames() { return store.activeMutes().stream().map(PublicChatMute::playerName).toList(); }

    public CompletionStage<PublicChatMute> mute(String name, long durationMillis, String reason, String actor) {
        return plugin.getPlayerManager().resolvePlayerUUID(name).thenCompose(id -> CompletableFuture.supplyAsync(() -> {
            try { return store.mute(id, name, durationMillis, reason, actor); }
            catch (IOException error) { throw new CompletionException(error); }
        }, writes));
    }

    public CompletionStage<Boolean> unmute(String name) {
        Player online = Bukkit.getPlayerExact(name);
        CompletionStage<UUID> identity;
        if (online != null) identity = CompletableFuture.completedFuture(online.getUniqueId());
        else {
            PublicChatMute stored = store.activeMutes().stream()
                    .filter(mute -> mute.playerName().equalsIgnoreCase(name)).findFirst().orElse(null);
            identity = stored == null ? plugin.getPlayerManager().resolvePlayerUUID(name)
                    : CompletableFuture.completedFuture(stored.playerId());
        }
        return identity.thenCompose(id -> CompletableFuture.supplyAsync(() -> {
            try { return store.unmute(id); }
            catch (IOException error) { throw new CompletionException(error); }
        }, writes));
    }
}
