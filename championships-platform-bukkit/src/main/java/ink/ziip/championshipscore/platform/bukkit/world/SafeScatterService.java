package ink.ziip.championshipscore.platform.bukkit.world;

import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Finds safe scatter locations without synchronously loading chunks. Block inspection is executed by
 * the owning region and player mutations by each player's entity scheduler, on both Paper and Folia.
 */
public final class SafeScatterService {
    private static final int MAX_CONCURRENT_SEARCHES = 4;
    private static final Set<Biome> WATER_BIOMES = Set.of(
            Biome.OCEAN, Biome.DEEP_OCEAN, Biome.WARM_OCEAN, Biome.LUKEWARM_OCEAN,
            Biome.DEEP_LUKEWARM_OCEAN, Biome.COLD_OCEAN, Biome.DEEP_COLD_OCEAN,
            Biome.FROZEN_OCEAN, Biome.DEEP_FROZEN_OCEAN, Biome.RIVER, Biome.FROZEN_RIVER
    );

    private final PlatformScheduler scheduler;

    public SafeScatterService(Plugin plugin) {
        this.scheduler = new PlatformScheduler(plugin);
    }

    public void performScatterAsync(
            World world, List<Player> players, int radius, int maxTries, Runnable onComplete) {
        if (world == null || players.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        int discRadius = Math.max(1, radius);
        int tries = Math.max(8, maxTries);
        List<Player> playerSnapshot = List.copyOf(players);
        scheduler.supplyGlobal(() -> world.getSpawnLocation().clone()).thenAccept(spawn ->
                processPlayersAsync(world, spawn, playerSnapshot.size(), discRadius, tries, locations -> {
                    List<CompletableFuture<Void>> teleports = new ArrayList<>();
                    for (int i = 0; i < playerSnapshot.size(); i++) {
                        Location target = i < locations.size() ? locations.get(i) : spawn;
                        teleports.add(teleportReset(playerSnapshot.get(i), target));
                    }
                    CompletableFuture.allOf(teleports.toArray(CompletableFuture[]::new))
                            .whenComplete((ignored, error) -> {
                                if (onComplete != null) scheduler.runAt(spawn, onComplete);
                            });
                }));
    }

    private void processPlayersAsync(World world, Location spawn, int playerCount,
                                     int radius, int tries,
                                     Consumer<List<Location>> onAllDone) {
        if (playerCount == 0) {
            onAllDone.accept(List.of());
            return;
        }
        Location[] locations = new Location[playerCount];
        AtomicInteger nextIndex = new AtomicInteger();
        AtomicInteger remaining = new AtomicInteger(playerCount);
        int workers = Math.min(MAX_CONCURRENT_SEARCHES, playerCount);
        for (int worker = 0; worker < workers; worker++) {
            findNextSpotAsync(world, spawn, locations, nextIndex, remaining, radius, tries, onAllDone);
        }
    }

    private void findNextSpotAsync(World world, Location spawn, Location[] locations,
                                   AtomicInteger nextIndex, AtomicInteger remaining,
                                   int radius, int tries, Consumer<List<Location>> onAllDone) {
        int index = nextIndex.getAndIncrement();
        if (index >= locations.length) return;
        findSingleSpotAsync(world, spawn, radius, tries, location -> {
            locations[index] = location;
            if (remaining.decrementAndGet() == 0) {
                onAllDone.accept(List.of(locations));
                return;
            }
            findNextSpotAsync(world, spawn, locations, nextIndex, remaining, radius, tries, onAllDone);
        });
    }

    private void findSingleSpotAsync(World world, Location spawn, int radius, int triesLeft,
                                     Consumer<Location> callback) {
        if (triesLeft <= 0) {
            world.getChunkAtAsync(spawn)
                    .thenCompose(chunk -> scheduler.supplyAt(spawn, () -> fallbackWorldSpawn(world, spawn)))
                    .whenComplete((location, error) -> {
                        if (error == null) callback.accept(location);
                        else callback.accept(spawn.clone().add(0.5, 1.0, 0.5));
                    });
            return;
        }

        Random random = ThreadLocalRandom.current();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = radius * Math.sqrt(random.nextDouble());
        int x = spawn.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = spawn.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        Location candidateRegion = new Location(world, x, 0, z);

        world.getChunkAtAsync(candidateRegion)
                .thenCompose(chunk -> scheduler.supplyAt(candidateRegion, () -> toTopSafe(world, x, z)))
                .whenComplete((candidate, error) -> {
                    if (error != null) {
                        callback.accept(spawn.clone().add(0.5, 1.0, 0.5));
                    } else if (candidate != null) callback.accept(candidate);
                    else findSingleSpotAsync(world, spawn, radius, triesLeft - 1, callback);
                });
    }

    private Location toTopSafe(World world, int x, int z) {
        if (world.getEnvironment() == World.Environment.NETHER) return toNetherSafe(world, x, z);
        int surfaceY = world.getHighestBlockYAt(x, z);
        Biome biome = world.getBiome(x, surfaceY, z);
        if (biome != null && WATER_BIOMES.contains(biome)) return null;
        Block top = world.getBlockAt(x, surfaceY, z);
        if (top.getType().isAir()) top = world.getBlockAt(x, surfaceY - 1, z);
        int y = top.getY();
        if (!withinSafeY(world, y)) return null;
        if (!isSolidGround(top.getType()) || isHazardGround(top.getType())) return null;
        if (!isClearSpace(world.getBlockAt(x, y + 1, z).getType())) return null;
        if (!isClearSpace(world.getBlockAt(x, y + 2, z).getType())) return null;
        return new Location(world, x + 0.5, y + 1.01, z + 0.5);
    }

    private Location toNetherSafe(World world, int x, int z) {
        int top = Math.min(world.getMaxHeight() - 10, 100);
        int bottom = world.getMinHeight() + 4;
        for (int y = top; y >= bottom; y--) {
            Block ground = world.getBlockAt(x, y, z);
            if (!isSolidGround(ground.getType()) || isHazardGround(ground.getType())) continue;
            if (!isClearSpace(world.getBlockAt(x, y + 1, z).getType())) continue;
            if (!isClearSpace(world.getBlockAt(x, y + 2, z).getType())) continue;
            return new Location(world, x + 0.5, y + 1.01, z + 0.5);
        }
        return null;
    }

    private boolean withinSafeY(World world, int y) {
        return switch (world.getEnvironment()) {
            case NORMAL -> y >= 54 && y <= 300;
            case NETHER -> y >= world.getMinHeight() + 6 && y <= world.getMaxHeight() - 6;
            case THE_END -> y >= 40 && y <= 300;
            default -> true;
        };
    }

    private Location fallbackWorldSpawn(World world, Location spawn) {
        Location safe = toTopSafe(world, spawn.getBlockX(), spawn.getBlockZ());
        return safe != null ? safe : spawn.clone().add(0.5, 1.0, 0.5);
    }

    private CompletableFuture<Void> teleportReset(Player player, Location location) {
        return player.teleportAsync(location)
                .thenCompose(ignored -> scheduler.runEntityFuture(player, () -> {
                    player.setFallDistance(0f);
                    player.setFireTicks(0);
                }))
                .exceptionally(error -> null);
    }

    private boolean isClearSpace(Material material) {
        if (material.isAir()) return true;
        if (isLiquid(material)) return false;
        return !material.isOccluding();
    }

    private boolean isSolidGround(Material material) {
        return material != null && !material.isAir() && material.isSolid() && !isLiquid(material);
    }

    private boolean isLiquid(Material material) {
        return material == Material.WATER || material == Material.LAVA;
    }

    private boolean isHazardGround(Material material) {
        return switch (material) {
            case SAND, RED_SAND, GRAVEL, CACTUS, CAMPFIRE, SOUL_CAMPFIRE,
                 MAGMA_BLOCK, SWEET_BERRY_BUSH, POWDER_SNOW -> true;
            default -> false;
        };
    }
}
