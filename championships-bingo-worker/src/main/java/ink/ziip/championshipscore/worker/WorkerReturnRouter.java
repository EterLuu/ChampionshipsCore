package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.proxy.PluginMessagePlayerRouter;
import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Worker-only return queue. A successful plugin-message write is not a proxy transfer acknowledgement,
 * so requests stay queued until the player actually leaves this backend.
 */
final class WorkerReturnRouter implements AutoCloseable {
    private static final long RETRY_PERIOD_TICKS = 40L;

    private final Plugin plugin;
    private final PluginMessagePlayerRouter router;
    private final String returnServer;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final ScheduledTask retryTask;

    WorkerReturnRouter(Plugin plugin, PluginMessagePlayerRouter router, String returnServer) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.router = Objects.requireNonNull(router, "router");
        this.returnServer = requireServer(returnServer);
        this.retryTask = new PlatformScheduler(plugin).runGlobalTimer(
                this::retryPending, RETRY_PERIOD_TICKS, RETRY_PERIOD_TICKS);
    }

    void request(Player player) {
        Objects.requireNonNull(player, "player");
        request(player.getUniqueId());
    }

    void request(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        pending.add(playerId);
        attempt(playerId);
    }

    void cancel(UUID playerId) {
        pending.remove(playerId);
    }

    private void retryPending() {
        for (UUID playerId : Set.copyOf(pending)) attempt(playerId);
    }

    private void attempt(UUID playerId) {
        if (!pending.contains(playerId)) return;
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            pending.remove(playerId);
            return;
        }
        router.route(player, returnServer).exceptionally(failure -> null);
    }

    @Override
    public void close() {
        retryTask.cancel();
        pending.clear();
    }

    private static String requireServer(String server) {
        String value = Objects.requireNonNull(server, "returnServer").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("returnServer must not be blank");
        return value;
    }
}
