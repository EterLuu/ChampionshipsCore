package ink.ziip.championshipscore.util.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Small scheduler facade shared by Paper and Folia.
 *
 * <p>Paper exposes the Folia scheduler API too, so callers can express the
 * ownership of their work without maintaining separate server-specific paths.</p>
 */
public final class FoliaScheduler {
    private static final long MILLIS_PER_TICK = 50L;

    private final Plugin plugin;
    private final Supplier<Location> locationSupplier;

    private FoliaScheduler(Plugin plugin, Supplier<Location> locationSupplier) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.locationSupplier = locationSupplier;
    }

    public static FoliaScheduler global(Plugin plugin) {
        return new FoliaScheduler(plugin, null);
    }

    public static FoliaScheduler region(Plugin plugin, Supplier<Location> locationSupplier) {
        return new FoliaScheduler(plugin, Objects.requireNonNull(locationSupplier, "locationSupplier"));
    }

    public ScheduledTask runTask(Runnable task) {
        return runTask(ignored -> task.run());
    }

    public ScheduledTask runTask(Plugin ignoredPlugin, Runnable task) {
        return runTask(task);
    }

    public ScheduledTask runTask(Plugin ignoredPlugin, Consumer<ScheduledTask> task) {
        return runTask(task);
    }

    public ScheduledTask runTask(Consumer<ScheduledTask> task) {
        if (locationSupplier == null) {
            return server().getGlobalRegionScheduler().run(plugin, task);
        }
        return server().getRegionScheduler().run(plugin, requireRegionLocation(), task);
    }

    public ScheduledTask runTaskLater(Runnable task, long delayTicks) {
        return runTaskLater(ignored -> task.run(), delayTicks);
    }

    public ScheduledTask runTaskLater(Plugin ignoredPlugin, Runnable task, long delayTicks) {
        return runTaskLater(task, delayTicks);
    }

    public ScheduledTask runTaskLater(Consumer<ScheduledTask> task, long delayTicks) {
        if (locationSupplier == null) {
            return server().getGlobalRegionScheduler().runDelayed(plugin, task, validTicks(delayTicks));
        }
        return server().getRegionScheduler().runDelayed(
                plugin, requireRegionLocation(), task, validTicks(delayTicks));
    }

    public ScheduledTask runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        return runTaskTimer(ignored -> task.run(), delayTicks, periodTicks);
    }

    public ScheduledTask runTaskTimer(
            Plugin ignoredPlugin, Runnable task, long delayTicks, long periodTicks) {
        return runTaskTimer(task, delayTicks, periodTicks);
    }

    public ScheduledTask runTaskTimer(
            Plugin ignoredPlugin, Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        return runTaskTimer(task, delayTicks, periodTicks);
    }

    public ScheduledTask runTaskTimer(Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        if (locationSupplier == null) {
            return server().getGlobalRegionScheduler().runAtFixedRate(
                    plugin, task, validTicks(delayTicks), validTicks(periodTicks));
        }
        return server().getRegionScheduler().runAtFixedRate(
                plugin, requireRegionLocation(), task, validTicks(delayTicks), validTicks(periodTicks));
    }

    public ScheduledTask runTaskAsynchronously(Runnable task) {
        return runTaskAsynchronously(ignored -> task.run());
    }

    public ScheduledTask runTaskAsynchronously(Plugin ignoredPlugin, Runnable task) {
        return runTaskAsynchronously(task);
    }

    public ScheduledTask runTaskAsynchronously(Consumer<ScheduledTask> task) {
        return server().getAsyncScheduler().runNow(plugin, task);
    }

    public CompletableFuture<Void> runGlobalFuture(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        server().getGlobalRegionScheduler().execute(plugin, () -> completeFuture(task, future));
        return future;
    }

    public <T> CompletableFuture<T> supplyGlobal(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        server().getGlobalRegionScheduler().execute(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public CompletableFuture<Void> runAsyncFuture(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        server().getAsyncScheduler().runNow(plugin, ignored -> completeFuture(task, future));
        return future;
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        server().getAsyncScheduler().runNow(plugin, ignored -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public ScheduledTask runTaskLaterAsynchronously(Runnable task, long delayTicks) {
        return server().getAsyncScheduler().runDelayed(
                plugin, ignored -> task.run(), ticksToMillis(delayTicks), TimeUnit.MILLISECONDS);
    }

    public ScheduledTask runTaskLaterAsynchronously(
            Plugin ignoredPlugin, Runnable task, long delayTicks) {
        return runTaskLaterAsynchronously(task, delayTicks);
    }

    public ScheduledTask runTaskTimerAsynchronously(Runnable task, long delayTicks, long periodTicks) {
        return server().getAsyncScheduler().runAtFixedRate(
                plugin,
                ignored -> task.run(),
                ticksToMillis(delayTicks),
                ticksToMillis(periodTicks),
                TimeUnit.MILLISECONDS);
    }

    public ScheduledTask runTaskTimerAsynchronously(
            Plugin ignoredPlugin, Runnable task, long delayTicks, long periodTicks) {
        return runTaskTimerAsynchronously(task, delayTicks, periodTicks);
    }

    public void runEntity(Entity entity, Runnable task) {
        entity.getScheduler().execute(plugin, task, null, 1L);
    }

    public CompletableFuture<Void> runEntityFuture(Entity entity, Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        boolean scheduled = entity.getScheduler().execute(
                plugin,
                () -> completeFuture(task, future),
                () -> future.complete(null),
                1L);
        if (!scheduled) {
            future.complete(null);
        }
        return future;
    }

    public void runEntityLater(Entity entity, Runnable task, long delayTicks) {
        entity.getScheduler().runDelayed(plugin, ignored -> task.run(), null, validTicks(delayTicks));
    }

    public ScheduledTask runEntityTimer(
            Entity entity, Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        return entity.getScheduler().runAtFixedRate(
                plugin, task, null, validTicks(delayTicks), validTicks(periodTicks));
    }

    public void runAtLocation(Location location, Runnable task) {
        server().getRegionScheduler().execute(plugin, requireLocation(location), task);
    }

    public void runAtLocationLater(Location location, Runnable task, long delayTicks) {
        server().getRegionScheduler().runDelayed(
                plugin, requireLocation(location), ignored -> task.run(), validTicks(delayTicks));
    }

    public CompletableFuture<Void> runAtLocationFuture(Location location, Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        server().getRegionScheduler().execute(
                plugin, requireLocation(location), () -> completeFuture(task, future));
        return future;
    }

    public <T> CompletableFuture<T> supplyAtLocation(Location location, Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        server().getRegionScheduler().execute(plugin, requireLocation(location), () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public void cancelGlobalAndAsyncTasks() {
        server().getGlobalRegionScheduler().cancelTasks(plugin);
        server().getAsyncScheduler().cancelTasks(plugin);
    }

    private Location requireRegionLocation() {
        return requireLocation(Objects.requireNonNull(locationSupplier, "locationSupplier").get());
    }

    private static Location requireLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalStateException("A region task requires a location in a loaded world");
        }
        return location;
    }

    private Server server() {
        return plugin.getServer();
    }

    private static long validTicks(long ticks) {
        return Math.max(1L, ticks);
    }

    private static long ticksToMillis(long ticks) {
        return validTicks(ticks) * MILLIS_PER_TICK;
    }

    private static void completeFuture(Runnable task, CompletableFuture<Void> future) {
        try {
            task.run();
            future.complete(null);
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }
}
