package ink.ziip.championshipscore.platform.bukkit.scheduler;

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
 * Paper/Folia scheduler facade which makes the ownership target explicit at each call site.
 * Paper exposes the same scheduler API, so consumers do not need a server-type branch.
 */
public final class PlatformScheduler {
    private static final long MILLIS_PER_TICK = 50L;

    private final Plugin plugin;

    public PlatformScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public ScheduledTask runGlobal(Runnable task) {
        return plugin.getServer().getGlobalRegionScheduler().run(plugin, ignored -> task.run());
    }

    public ScheduledTask runGlobalLater(Runnable task, long delayTicks) {
        return plugin.getServer().getGlobalRegionScheduler()
                .runDelayed(plugin, ignored -> task.run(), validTicks(delayTicks));
    }

    public ScheduledTask runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        return plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin, ignored -> task.run(), validTicks(delayTicks), validTicks(periodTicks));
    }

    public ScheduledTask runAsync(Runnable task) {
        return plugin.getServer().getAsyncScheduler().runNow(plugin, ignored -> task.run());
    }

    public ScheduledTask runAsyncLater(Runnable task, long delayTicks) {
        return plugin.getServer().getAsyncScheduler().runDelayed(
                plugin, ignored -> task.run(), ticksToMillis(delayTicks), TimeUnit.MILLISECONDS);
    }

    public ScheduledTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        return plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin, ignored -> task.run(), ticksToMillis(delayTicks), ticksToMillis(periodTicks),
                TimeUnit.MILLISECONDS);
    }

    public void runEntity(Entity entity, Runnable task) {
        Objects.requireNonNull(entity, "entity").getScheduler().execute(plugin, task, null, 1L);
    }

    public void runEntityLater(Entity entity, Runnable task, long delayTicks) {
        Objects.requireNonNull(entity, "entity").getScheduler()
                .runDelayed(plugin, ignored -> task.run(), null, validTicks(delayTicks));
    }

    public ScheduledTask runEntityTimer(
            Entity entity, Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        return Objects.requireNonNull(entity, "entity").getScheduler().runAtFixedRate(
                plugin, task, null, validTicks(delayTicks), validTicks(periodTicks));
    }

    public void runAt(Location location, Runnable task) {
        plugin.getServer().getRegionScheduler().execute(plugin, requireLocation(location), task);
    }

    public void runAtLater(Location location, Runnable task, long delayTicks) {
        plugin.getServer().getRegionScheduler().runDelayed(
                plugin, requireLocation(location), ignored -> task.run(), validTicks(delayTicks));
    }

    public CompletableFuture<Void> runGlobalFuture(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> complete(task, future));
        return future;
    }

    public CompletableFuture<Void> runAsyncFuture(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        plugin.getServer().getAsyncScheduler().runNow(plugin, ignored -> complete(task, future));
        return future;
    }

    public CompletableFuture<Void> runEntityFuture(Entity entity, Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        boolean scheduled = Objects.requireNonNull(entity, "entity").getScheduler().execute(
                plugin, () -> complete(task, future), () -> future.complete(null), 1L);
        if (!scheduled) future.complete(null);
        return future;
    }

    public <T> CompletableFuture<T> supplyEntity(Entity entity, Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        boolean scheduled = Objects.requireNonNull(entity, "entity").getScheduler().execute(
                plugin, () -> complete(supplier, future), () -> future.complete(null), 1L);
        if (!scheduled) future.complete(null);
        return future;
    }

    public CompletableFuture<Void> runAtFuture(Location location, Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        plugin.getServer().getRegionScheduler().execute(
                plugin, requireLocation(location), () -> complete(task, future));
        return future;
    }

    public <T> CompletableFuture<T> supplyGlobal(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        plugin.getServer().getGlobalRegionScheduler().execute(
                plugin, () -> complete(supplier, future));
        return future;
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        plugin.getServer().getAsyncScheduler().runNow(plugin, ignored -> complete(supplier, future));
        return future;
    }

    public <T> CompletableFuture<T> supplyAt(Location location, Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        plugin.getServer().getRegionScheduler().execute(
                plugin, requireLocation(location), () -> complete(supplier, future));
        return future;
    }

    public void cancelGlobalAndAsyncTasks() {
        Server server = plugin.getServer();
        server.getGlobalRegionScheduler().cancelTasks(plugin);
        server.getAsyncScheduler().cancelTasks(plugin);
    }

    private static Location requireLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("A region task requires a location in a loaded world");
        }
        return location;
    }

    private static long validTicks(long ticks) {
        return Math.max(1L, ticks);
    }

    private static long ticksToMillis(long ticks) {
        return validTicks(ticks) * MILLIS_PER_TICK;
    }

    private static void complete(Runnable task, CompletableFuture<Void> future) {
        try {
            task.run();
            future.complete(null);
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private static <T> void complete(Supplier<T> supplier, CompletableFuture<T> future) {
        try {
            future.complete(supplier.get());
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }
}
