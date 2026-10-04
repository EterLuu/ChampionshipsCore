package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;
import ink.ziip.championshipscore.platform.bukkit.world.SafeScatterService;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

/**
 * Resolves one safe destination per team and reuses it for every member, including late arrivals.
 */
final class WorkerTeamScatterService {
    private static final int MAX_CONCURRENT_SEARCHES = 4;
    private final LocationSearch search;
    private final BiFunction<Player, Location, CompletableFuture<Boolean>> teleport;
    private final Runnable cancelSearch;
    private final Map<Integer, CompletableFuture<Location>> destinations =
            new ConcurrentHashMap<>();
    private final Map<Player, CompletableFuture<Void>> teleports = new ConcurrentHashMap<>();
    private final Set<UUID> placedPlayers = ConcurrentHashMap.newKeySet();
    private volatile boolean cancelled;

    WorkerTeamScatterService(Plugin plugin) {
        PlatformScheduler scheduler = new PlatformScheduler(plugin);
        SafeScatterService safeLocations = new SafeScatterService(plugin);
        search =
                (world, area, maxTries) ->
                        safeLocations.findSafeLocationAsync(
                                world,
                                area.minX(),
                                area.maxX(),
                                area.minZ(),
                                area.maxZ(),
                                maxTries);
        cancelSearch = safeLocations::cancelPending;
        teleport =
                (player, location) ->
                        scheduler
                                .supplyEntity(
                                        player,
                                        () -> {
                                            if (cancelled || !player.isOnline())
                                                return CompletableFuture.completedFuture(false);
                                            return player.teleportAsync(location.clone())
                                                    .thenCompose(
                                                            success -> {
                                                                if (!Boolean.TRUE.equals(success))
                                                                    return CompletableFuture
                                                                            .completedFuture(false);
                                                                placedPlayers.add(
                                                                        player.getUniqueId());
                                                                return scheduler
                                                                        .runEntityFuture(
                                                                                player,
                                                                                () -> {
                                                                                    player
                                                                                            .setFallDistance(
                                                                                                    0F);
                                                                                    player
                                                                                            .setFireTicks(
                                                                                                    0);
                                                                                })
                                                                        .thenApply(ignored -> true);
                                                            });
                                        })
                                .thenCompose(
                                        result ->
                                                result == null
                                                        ? CompletableFuture.completedFuture(false)
                                                        : result);
    }

    WorkerTeamScatterService(
            LocationSearch search,
            BiFunction<Player, Location, CompletableFuture<Boolean>> teleport) {
        this.search = search;
        this.teleport = teleport;
        this.cancelSearch = () -> {};
    }

    CompletableFuture<Void> prepareAsync(World world, Collection<Integer> teamIds, int maxTries) {
        if (cancelled) return CompletableFuture.failedFuture(new CancellationException());
        Map<Integer, WorkerScatterPlan.SearchArea> plan =
                WorkerScatterPlan.create(teamIds, ThreadLocalRandom.current());
        for (int teamId : plan.keySet()) destinations.put(teamId, new CompletableFuture<>());
        List<Map.Entry<Integer, WorkerScatterPlan.SearchArea>> areas =
                new ArrayList<>(plan.entrySet());
        AtomicInteger next = new AtomicInteger();
        for (int worker = 0; worker < Math.min(MAX_CONCURRENT_SEARCHES, areas.size()); worker++)
            resolveNext(world, areas, next, maxTries);
        return CompletableFuture.allOf(destinations.values().toArray(CompletableFuture[]::new));
    }

    private void resolveNext(
            World world,
            List<Map.Entry<Integer, WorkerScatterPlan.SearchArea>> areas,
            AtomicInteger next,
            int maxTries) {
        if (cancelled) return;
        int index = next.getAndIncrement();
        if (index >= areas.size()) return;
        var entry = areas.get(index);
        CompletableFuture<Location> result = destinations.get(entry.getKey());
        try {
            WorkerScatterPlan.SearchArea preferred = entry.getValue();
            WorkerScatterPlan.SearchArea expanded =
                    WorkerScatterPlan.expand(preferred, areas.size());
            search.find(world, preferred, maxTries)
                    .exceptionallyCompose(
                            failure -> {
                                if (cancelled || preferred.equals(expanded))
                                    return CompletableFuture.failedFuture(failure);
                                return search.find(world, expanded, maxTries);
                            })
                    .whenComplete(
                            (location, failure) -> {
                                if (failure != null) result.completeExceptionally(failure);
                                else if (location == null)
                                    result.completeExceptionally(
                                            new IllegalStateException("Missing team spawn"));
                                else result.complete(location.clone());
                                resolveNext(world, areas, next, maxTries);
                            });
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
            resolveNext(world, areas, next, maxTries);
        }
    }

    CompletableFuture<Void> teleportAsync(Player player, int teamId) {
        if (cancelled) return CompletableFuture.failedFuture(new CancellationException());
        CompletableFuture<Location> destination = destinations.get(teamId);
        if (destination == null)
            return CompletableFuture.failedFuture(
                    new IllegalStateException("No Bingo scatter destination for team " + teamId));
        // A reconnect uses a new Player entity. Concurrent requests for the same entity share a
        // teleport.
        return teleports.computeIfAbsent(
                player,
                ignored ->
                        destination.thenCompose(
                                location -> {
                                    if (cancelled)
                                        return CompletableFuture.failedFuture(
                                                new CancellationException());
                                    return teleport.apply(player, location.clone())
                                            .thenAccept(
                                                    success -> {
                                                        if (!Boolean.TRUE.equals(success)) {
                                                            if (player.isOnline())
                                                                throw new IllegalStateException(
                                                                        "Bingo team teleport was"
                                                                                + " rejected: "
                                                                                + player
                                                                                        .getUniqueId());
                                                            return;
                                                        }
                                                        placedPlayers.add(player.getUniqueId());
                                                    });
                                }));
    }

    boolean hasPlacedPlayer(UUID playerId) {
        return placedPlayers.contains(playerId);
    }

    void cancelPending() {
        cancelled = true;
        cancelSearch.run();
        destinations
                .values()
                .forEach(future -> future.completeExceptionally(new CancellationException()));
    }

    @FunctionalInterface
    interface LocationSearch {
        CompletableFuture<Location> find(
                World world, WorkerScatterPlan.SearchArea area, int maxTries);
    }
}
