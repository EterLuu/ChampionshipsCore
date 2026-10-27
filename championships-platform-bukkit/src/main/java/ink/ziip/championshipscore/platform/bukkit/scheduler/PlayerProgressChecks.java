package ink.ziip.championshipscore.platform.bukkit.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Coalesces next-tick progress scans per connection and session, on the player's entity thread. */
public final class PlayerProgressChecks implements AutoCloseable {
    private final Plugin plugin;
    private final ConcurrentHashMap<UUID, Request> pending = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();

    public PlayerProgressChecks(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
    }

    public void request(Player player, Object session, BooleanSupplier current, Runnable check) {
        if (closed.get() || !plugin.isEnabled()) return;
        Objects.requireNonNull(session);
        UUID id = player.getUniqueId();
        long epoch = generation.get();
        Request request = new Request(player, session);
        Request[] replaced = new Request[1];
        Request selected =
                pending.compute(
                        id,
                        (ignored, previous) -> {
                            if (previous != null
                                    && previous.player == player
                                    && previous.session == session) return previous;
                            replaced[0] = previous;
                            return request;
                        });
        if (selected != request) return;
        if (replaced[0] != null) replaced[0].cancel();
        if (closed.get() || generation.get() != epoch) {
            discard(id, request);
            return;
        }
        try {
            ScheduledTask task =
                    player.getScheduler()
                            .runDelayed(
                                    plugin,
                                    ignored -> {
                                        if (!pending.remove(id, request)) return;
                                        request.finish();
                                        if (!closed.get()
                                                && generation.get() == epoch
                                                && plugin.isEnabled()
                                                && player.isOnline()
                                                && plugin.getServer().getPlayer(id) == player
                                                && current.getAsBoolean()) check.run();
                                    },
                                    () -> discard(id, request),
                                    1L);
            request.attach(task);
            if (task == null) discard(id, request);
        } catch (RuntimeException failure) {
            discard(id, request);
            throw failure;
        }
    }

    public void cancel(Player player) {
        UUID id = player.getUniqueId();
        Request request = pending.get(id);
        if (request != null && request.player == player) discard(id, request);
    }

    public void clear() {
        generation.incrementAndGet();
        pending.forEach(this::discard);
    }

    @Override
    public void close() {
        closed.set(true);
        clear();
    }

    private void discard(UUID id, Request request) {
        pending.remove(id, request);
        request.cancel();
    }

    private static final class Request {
        final Player player;
        final Object session;
        private ScheduledTask task;
        private boolean finished;

        Request(Player player, Object session) {
            this.player = player;
            this.session = session;
        }

        synchronized void attach(ScheduledTask task) {
            if (finished) {
                if (task != null) task.cancel();
            } else this.task = task;
        }

        synchronized void finish() {
            finished = true;
            task = null;
        }

        synchronized void cancel() {
            finished = true;
            if (task != null) task.cancel();
            task = null;
        }
    }
}
