package ink.ziip.championshipscore.platform.bukkit.player;

import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Pending gameplay teleports owned by one match; this service does not own player roles. */
public final class PlayerTargetTeleports implements AutoCloseable {
    private final Plugin plugin;
    private final PlatformScheduler scheduler;
    private final Map<UUID, Request> pending = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public PlayerTargetTeleports(Plugin plugin) {
        this.plugin = plugin;
        this.scheduler = new PlatformScheduler(plugin);
    }

    /**
     * Start on the viewer's entity thread. Viewer admission and feedback run there; target
     * admission and location capture run on the target's thread. The final target-current guard
     * must inspect owner snapshots only, without accessing another entity's mutable state.
     */
    public CompletableFuture<Boolean> request(
            Player viewer,
            UUID targetId,
            BooleanSupplier viewerAllowed,
            Predicate<Player> targetAllowed,
            BooleanSupplier targetCurrent,
            Runnable feedback) {
        cancel(viewer.getUniqueId());
        if (closed || !plugin.isEnabled() || !connected(viewer) || !viewerAllowed.getAsBoolean())
            return CompletableFuture.completedFuture(false);
        Player target = plugin.getServer().getPlayer(targetId);
        if (target == null || target == viewer) return CompletableFuture.completedFuture(false);
        var request =
                new Request(viewer, target, viewerAllowed, targetAllowed, targetCurrent, feedback);
        pending.put(viewer.getUniqueId(), request);
        request.result.whenComplete(
                (value, failure) -> pending.remove(viewer.getUniqueId(), request));
        scheduler
                .supplyEntity(
                        target, () -> request.currentTarget() ? target.getLocation().clone() : null)
                .thenCompose(
                        location -> {
                            if (location == null
                                    || location.getWorld() == null
                                    || request.result.isDone())
                                return CompletableFuture.<Location>completedFuture(null);
                            return location.getWorld()
                                    .getChunkAtAsync(location)
                                    .thenApply(chunk -> location);
                        })
                .thenCompose(
                        location ->
                                location == null || request.result.isDone()
                                        ? CompletableFuture.<Location>completedFuture(null)
                                        : scheduler.supplyEntity(
                                                target,
                                                () -> request.currentTarget() ? location : null))
                .whenComplete(
                        (location, failure) -> {
                            if (failure != null) request.result.completeExceptionally(failure);
                            else if (location == null || !plugin.isEnabled())
                                request.result.complete(false);
                            else request.queue(() -> request.apply(location));
                        });
        return request.result;
    }

    /** Cancels this viewer's request. Starting a newer selection always calls this first. */
    public void cancel(UUID viewer) {
        var request = pending.remove(viewer);
        if (request != null) request.result.complete(false);
    }

    /** Disconnect, role change or death invalidates outgoing and incoming requests. */
    public void cancelPlayer(UUID player) {
        for (var request : List.copyOf(pending.values()))
            if (request.viewer.getUniqueId().equals(player)
                    || request.target.getUniqueId().equals(player)) request.result.complete(false);
    }

    public void clear() {
        List.copyOf(pending.values()).forEach(request -> request.result.complete(false));
    }

    @Override
    public void close() {
        closed = true;
        clear();
    }

    private boolean connected(Player player) {
        return player.isOnline() && plugin.getServer().getPlayer(player.getUniqueId()) == player;
    }

    private final class Request {
        private final Player viewer;
        private final Player target;
        private final BooleanSupplier viewerAllowed;
        private final Predicate<Player> targetAllowed;
        private final BooleanSupplier targetCurrent;
        private final Runnable feedback;
        private final CompletableFuture<Boolean> result = new CompletableFuture<>();

        Request(
                Player viewer,
                Player target,
                BooleanSupplier viewerAllowed,
                Predicate<Player> targetAllowed,
                BooleanSupplier targetCurrent,
                Runnable feedback) {
            this.viewer = viewer;
            this.target = target;
            this.viewerAllowed = viewerAllowed;
            this.targetAllowed = targetAllowed;
            this.targetCurrent = targetCurrent;
            this.feedback = feedback;
        }

        private boolean current() {
            return !closed
                    && plugin.isEnabled()
                    && !result.isDone()
                    && pending.get(viewer.getUniqueId()) == this;
        }

        private boolean currentTarget() {
            return current()
                    && connected(target)
                    && targetCurrent.getAsBoolean()
                    && targetAllowed.test(target);
        }

        private boolean currentViewer() {
            return current()
                    && connected(viewer)
                    && connected(target)
                    && viewerAllowed.getAsBoolean()
                    && targetCurrent.getAsBoolean();
        }

        private void queue(Runnable action) {
            if (!current()) {
                result.complete(false);
                return;
            }
            try {
                if (!scheduler.runEntity(viewer, action, () -> result.complete(false)))
                    result.complete(false);
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        }

        private void apply(Location location) {
            if (!currentViewer()) {
                result.complete(false);
                return;
            }
            try {
                viewer.teleportAsync(location)
                        .whenComplete(
                                (success, failure) -> {
                                    if (failure != null) result.completeExceptionally(failure);
                                    else if (!Boolean.TRUE.equals(success)) result.complete(false);
                                    else
                                        queue(
                                                () -> {
                                                    try {
                                                        if (currentViewer()) feedback.run();
                                                        result.complete(true);
                                                    } catch (RuntimeException error) {
                                                        result.completeExceptionally(error);
                                                    }
                                                });
                                });
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        }
    }
}
