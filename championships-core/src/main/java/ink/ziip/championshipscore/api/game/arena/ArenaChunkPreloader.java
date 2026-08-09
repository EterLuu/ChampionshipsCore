package ink.ziip.championshipscore.api.game.arena;

import ink.ziip.championshipscore.ChampionshipsCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
            futures.add(world.getChunkAtAsync(ticket.x(), ticket.z(), true).thenAccept(chunk -> {
                if (chunk.addPluginChunkTicket(plugin)) ownedTickets.add(ticket);
            }));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    public static void release(@NotNull ChampionshipsCore plugin, @NotNull Set<ChunkTicket> ownedTickets) {
        for (ChunkTicket ticket : List.copyOf(ownedTickets)) {
            World world = Bukkit.getWorld(ticket.worldName());
            if (world != null) world.removePluginChunkTicket(ticket.x(), ticket.z(), plugin);
            ownedTickets.remove(ticket);
        }
    }

    public record ChunkTicket(@NotNull String worldName, int x, int z) {
    }
}
