package ink.ziip.championshipscore.platform.bukkit.player;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Waits for native respawn on the entity thread; roles and game state remain owned by callers. */
public final class PlayerRespawnRecovery implements AutoCloseable {
    private final Plugin plugin;
    private final Map<UUID, Request> pending = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public PlayerRespawnRecovery(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
    }

    /** A new death supersedes all waiting, warming and restoring work for that connection. */
    public CompletableFuture<Boolean> death(
            Player player,
            Object owner,
            BooleanSupplier current,
            Supplier<Location> destination,
            Runnable restore) {
        return request(player, owner, current, destination, restore, true);
    }

    /**
     * Paper's event coalesces with a death already being watched; it also covers event-only paths.
     */
    public CompletableFuture<Boolean> respawn(
            Player player,
            Object owner,
            BooleanSupplier current,
            Supplier<Location> destination,
            Runnable restore) {
        return request(player, owner, current, destination, restore, false);
    }

    private CompletableFuture<Boolean> request(
            Player player,
            Object owner,
            BooleanSupplier current,
            Supplier<Location> destination,
            Runnable restore,
            boolean replace) {
        Objects.requireNonNull(owner);
        if (closed || !plugin.isEnabled()) return CompletableFuture.completedFuture(false);
        UUID id = player.getUniqueId();
        var next = new Request(player, owner, current, destination, restore);
        Request[] old = new Request[1];
        var selected =
                pending.compute(
                        id,
                        (key, previous) -> {
                            if (!replace
                                    && previous != null
                                    && previous.player == player
                                    && previous.owner == owner) return previous;
                            old[0] = previous;
                            return next;
                        });
        if (selected != next) return selected.result;
        if (old[0] != null) old[0].result.complete(false);
        next.result.whenComplete(
                (value, error) -> {
                    pending.remove(id, next);
                    next.cancelTask();
                });
        next.queue(next::poll);
        return next.result;
    }

    public void cancel(Player player) {
        var request = pending.get(player.getUniqueId());
        if (request != null && request.player == player) request.result.complete(false);
    }

    public void clear() {
        List.copyOf(pending.values()).forEach(r -> r.result.complete(false));
    }

    @Override
    public void close() {
        closed = true;
        clear();
    }

    private final class Request {
        final Player player;
        final Object owner;
        final BooleanSupplier ownerCurrent;
        final Supplier<Location> destination;
        final Runnable restore;
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        private ScheduledTask task;

        Request(
                Player player,
                Object owner,
                BooleanSupplier current,
                Supplier<Location> destination,
                Runnable restore) {
            this.player = player;
            this.owner = owner;
            this.ownerCurrent = current;
            this.destination = destination;
            this.restore = restore;
        }

        boolean tokenCurrent() {
            return !closed
                    && plugin.isEnabled()
                    && !result.isDone()
                    && pending.get(player.getUniqueId()) == this;
        }

        // Called only by entity scheduler callbacks, including before initiating a teleport.
        boolean current() {
            return tokenCurrent()
                    && player.isOnline()
                    && plugin.getServer().getPlayer(player.getUniqueId()) == player
                    && ownerCurrent.getAsBoolean();
        }

        synchronized void attach(ScheduledTask scheduled) {
            if (result.isDone()) {
                if (scheduled != null) scheduled.cancel();
            } else task = scheduled;
        }

        synchronized void cancelTask() {
            if (task != null) task.cancel();
            task = null;
        }

        void queue(Runnable action) {
            if (!tokenCurrent()) {
                result.complete(false);
                return;
            }
            try {
                var scheduled =
                        player.getScheduler()
                                .runDelayed(
                                        plugin,
                                        ignored -> {
                                            try {
                                                if (current()) action.run();
                                                else result.complete(false);
                                            } catch (RuntimeException error) {
                                                result.completeExceptionally(error);
                                            }
                                        },
                                        () -> result.complete(false),
                                        1L);
                attach(scheduled);
                if (scheduled == null) result.complete(false);
            } catch (RuntimeException error) {
                result.completeExceptionally(error);
            }
        }

        void poll() {
            if (player.isDead() || player.getHealth() <= 0) {
                queue(this::poll);
                return;
            }
            Location target = destination.get();
            if (target == null) {
                finish();
                return;
            }
            target = target.clone();
            if (target.getWorld() == null) {
                result.complete(false);
                return;
            }
            Location at = player.getLocation();
            if (at.getWorld().equals(target.getWorld()) && at.distanceSquared(target) < 1.0e-6) {
                finish();
                return;
            }
            Location warmed = target;
            // Warm first, then validate the connection/owner on the entity thread before moving it.
            target.getWorld()
                    .getChunkAtAsync(target)
                    .thenApply(chunk -> warmed)
                    .orTimeout(60, TimeUnit.SECONDS)
                    .whenComplete(
                            (location, error) -> {
                                if (error != null) result.completeExceptionally(error);
                                else
                                    queue(
                                            () -> {
                                                if (player.isDead() || player.getHealth() <= 0) {
                                                    queue(this::poll);
                                                    return;
                                                }
                                                player.teleportAsync(location)
                                                        .whenComplete(
                                                                (success, failure) -> {
                                                                    if (failure != null)
                                                                        result
                                                                                .completeExceptionally(
                                                                                        failure);
                                                                    else if (!Boolean.TRUE.equals(
                                                                            success))
                                                                        result.complete(false);
                                                                    else queue(this::finish);
                                                                });
                                            });
                            });
        }

        void finish() {
            if (!current() || player.isDead() || player.getHealth() <= 0) {
                result.complete(false);
                return;
            }
            restore.run();
            result.complete(true);
        }
    }
}
