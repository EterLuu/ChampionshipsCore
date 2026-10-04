package ink.ziip.championshipscore.api.game.arena;

import ink.ziip.championshipscore.ChampionshipsCore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Asynchronously warms landing chunks and keeps them resident for the lifetime of a game round. */
public final class ArenaChunkPreloader {
    // Bukkit tickets belong to the plugin, not to an individual match. All access occurs on main.
    private static final Map<ChampionshipsCore, Map<ChunkTicket, Lease>> leases =
            new IdentityHashMap<>();

    private static final class Lease {
        private int users = 1;
        private final boolean owned;

        private Lease(boolean owned) {
            this.owned = owned;
        }
    }

    private ArenaChunkPreloader() {}

    public static @NotNull CompletableFuture<Void> preload(
            @NotNull ChampionshipsCore plugin,
            @NotNull Collection<Location> locations,
            int radius,
            @NotNull Set<ChunkTicket> ownedTickets) {
        return preload(plugin, locations, radius, ownedTickets, () -> plugin.isLoaded());
    }

    public static @NotNull CompletableFuture<Void> preload(
            @NotNull ChampionshipsCore plugin,
            @NotNull Collection<Location> locations,
            int radius,
            @NotNull Set<ChunkTicket> ownedTickets,
            @NotNull BooleanSupplier current) {
        Set<ChunkTicket> requested = new LinkedHashSet<>();
        for (Location location : locations) {
            if (location == null || location.getWorld() == null) continue;
            int centerX = location.getBlockX() >> 4;
            int centerZ = location.getBlockZ() >> 4;
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    requested.add(new ChunkTicket(location.getWorld().getName(), x, z));
                }
            }
        }

        List<CompletableFuture<?>> futures = new ArrayList<>(requested.size());
        for (ChunkTicket ticket : requested) {
            World world = Bukkit.getWorld(ticket.worldName());
            if (world == null) continue;
            futures.add(
                    world.getChunkAtAsync(ticket.x(), ticket.z(), true)
                            .thenCompose(
                                    chunk -> {
                                        CompletableFuture<Void> applied = new CompletableFuture<>();
                                        if (!current.getAsBoolean() || !plugin.isLoaded()) {
                                            applied.complete(null);
                                            return applied;
                                        }
                                        try {
                                            plugin.getServer()
                                                    .getScheduler()
                                                    .runTask(
                                                            plugin,
                                                            () -> {
                                                                try {
                                                                    if (current.getAsBoolean()
                                                                            && plugin.isLoaded()
                                                                            && ownedTickets.add(
                                                                                    ticket)) {
                                                                        var pluginLeases =
                                                                                leases
                                                                                        .computeIfAbsent(
                                                                                                plugin,
                                                                                                unused ->
                                                                                                        new HashMap<>());
                                                                        Lease lease =
                                                                                pluginLeases.get(
                                                                                        ticket);
                                                                        if (lease == null) {
                                                                            try {
                                                                                pluginLeases.put(
                                                                                        ticket,
                                                                                        new Lease(
                                                                                                chunk
                                                                                                        .addPluginChunkTicket(
                                                                                                                plugin)));
                                                                            } catch (
                                                                                    RuntimeException
                                                                                            failure) {
                                                                                ownedTickets.remove(
                                                                                        ticket);
                                                                                throw failure;
                                                                            }
                                                                        } else lease.users++;
                                                                    }
                                                                    applied.complete(null);
                                                                } catch (RuntimeException failure) {
                                                                    applied.completeExceptionally(
                                                                            failure);
                                                                }
                                                            });
                                        } catch (RuntimeException rejected) {
                                            applied.completeExceptionally(rejected);
                                        }
                                        return applied;
                                    }));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    public static void release(
            @NotNull ChampionshipsCore plugin, @NotNull Set<ChunkTicket> ownedTickets) {
        for (ChunkTicket ticket : List.copyOf(ownedTickets)) {
            var pluginLeases = leases.get(plugin);
            Lease lease = pluginLeases == null ? null : pluginLeases.get(ticket);
            if (lease != null && --lease.users == 0) {
                pluginLeases.remove(ticket);
                World world = Bukkit.getWorld(ticket.worldName());
                if (world != null && lease.owned)
                    world.removePluginChunkTicket(ticket.x(), ticket.z(), plugin);
                if (pluginLeases.isEmpty()) leases.remove(plugin);
            }
            ownedTickets.remove(ticket);
        }
    }

    public record ChunkTicket(@NotNull String worldName, int x, int z) {}
}
