package ink.ziip.championshipscore.api.game.arena;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

/** Asynchronously warms landing chunks and keeps them resident for the lifetime of a game round. */
public final class ArenaChunkPreloader {
    private ArenaChunkPreloader() {
    }

    public static @NotNull CompletableFuture<Void> preload(@NotNull ChampionshipsCore plugin,
                                                            @NotNull Collection<Location> locations,
                                                            int radius,
                                                            @NotNull Set<ChunkTicket> ownedTickets) {
        Set<ChunkTicket> requested = new LinkedHashSet<>();
        Map<String, World> worlds = new HashMap<>();
        for (Location location : locations) {
            if (location == null || location.getWorld() == null) continue;
            worlds.put(location.getWorld().getName(), location.getWorld());
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
            World world = worlds.get(ticket.worldName());
            if (world == null) continue;
            Location owner = new Location(world, (ticket.x() << 4) + 8, world.getMinHeight(),
                    (ticket.z() << 4) + 8);
            futures.add(world.getChunkAtAsync(ticket.x(), ticket.z(), true)
                    .thenCompose(chunk -> FoliaScheduler.global(plugin).supplyAtLocation(owner, () -> {
                        if (chunk.addPluginChunkTicket(plugin)) ownedTickets.add(ticket);
                        return null;
                    })));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    public static void release(@NotNull ChampionshipsCore plugin, @NotNull Set<ChunkTicket> ownedTickets) {
        if (!plugin.isEnabled()) {
            // Paper removes a disabled plugin's tickets during plugin teardown. Scheduling a region
            // callback from onDisable would be rejected because the plugin is already disabled.
            ownedTickets.clear();
            return;
        }
        for (ChunkTicket ticket : List.copyOf(ownedTickets)) {
            FoliaScheduler.global(plugin).supplyGlobal(() -> Bukkit.getWorld(ticket.worldName()))
                    .thenAccept(world -> {
                        if (world == null) {
                            ownedTickets.remove(ticket);
                            return;
                        }
                        Location owner = new Location(world, (ticket.x() << 4) + 8, world.getMinHeight(),
                                (ticket.z() << 4) + 8);
                        FoliaScheduler.global(plugin).runAtLocation(owner, () -> {
                            world.removePluginChunkTicket(ticket.x(), ticket.z(), plugin);
                            ownedTickets.remove(ticket);
                        });
                    });
        }
    }

    public record ChunkTicket(@NotNull String worldName, int x, int z) {
    }
}
