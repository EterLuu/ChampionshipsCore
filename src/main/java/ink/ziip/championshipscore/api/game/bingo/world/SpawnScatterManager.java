package ink.ziip.championshipscore.api.game.bingo.world;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Scatters players within a small disc around a world's spawn, picking a safe top-of-surface spot per
 * player (clear of water/lava/hazards, with headroom). Ported from minebingo, with the {@code Tasks}
 * wrapper replaced by the Paper/Folia schedulers. Chunk loads use Paper's async chunk API; each
 * safety scan is executed by the region that owns the candidate chunk and teleports use entity-safe APIs.
 */
public final class SpawnScatterManager {
    private static final Set<Biome> WATER_BIOMES = Set.of(
            Biome.OCEAN, Biome.DEEP_OCEAN, Biome.WARM_OCEAN, Biome.LUKEWARM_OCEAN,
            Biome.DEEP_LUKEWARM_OCEAN, Biome.COLD_OCEAN, Biome.DEEP_COLD_OCEAN,
            Biome.FROZEN_OCEAN, Biome.DEEP_FROZEN_OCEAN, Biome.RIVER, Biome.FROZEN_RIVER
    );

    private final ChampionshipsCore plugin;

    public SpawnScatterManager(ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    public void performScatterAsync(World world, List<Player> players, int radius, int maxTries, Runnable onComplete) {
        if (world == null || players.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        int discRadius = Math.max(1, radius);
        int tries = Math.max(8, maxTries);
        FoliaScheduler.global(plugin).supplyGlobal(() -> world.getSpawnLocation().clone())
                .thenAccept(spawn -> processPlayersAsync(
                        world, spawn, players, new ArrayList<>(), discRadius, tries, locations -> {
            List<CompletableFuture<Void>> teleports = new ArrayList<>();
            for (int i = 0; i < players.size(); i++) {
                Location loc = i < locations.size() ? locations.get(i) : spawn;
                teleports.add(teleportReset(players.get(i), loc));
            }
            CompletableFuture.allOf(teleports.toArray(CompletableFuture[]::new)).whenComplete((unused, error) -> {
                if (onComplete != null) {
                    FoliaScheduler.region(plugin, () -> spawn).runTask(onComplete);
                }
            });
        }));
    }

    private void processPlayersAsync(World world, Location spawn, List<Player> players,
                                     List<Location> taken, int radius, int tries,
                                     Consumer<List<Location>> onAllDone) {
        int index = taken.size();
        if (index >= players.size()) {
            onAllDone.accept(taken);
            return;
        }
        findSingleSpotAsync(world, spawn, radius, tries, loc -> {
            taken.add(loc);
            processPlayersAsync(world, spawn, players, taken, radius, tries, onAllDone);
        });
    }

    private void findSingleSpotAsync(World world, Location spawn, int radius, int triesLeft,
                                     Consumer<Location> callback) {
        if (triesLeft <= 0) {
            world.getChunkAtAsync(spawn)
                    .thenCompose(chunk -> FoliaScheduler.global(plugin).supplyAtLocation(
                            spawn, () -> fallbackWorldSpawn(world, spawn)))
                    .thenAccept(callback)
                    .exceptionally(error -> {
                        callback.accept(spawn.clone().add(0.5, 1.0, 0.5));
                        return null;
                    });
            return;
        }
        int cx = spawn.getBlockX();
        int cz = spawn.getBlockZ();
        Random random = ThreadLocalRandom.current();
        // Uniform sample over a disc: r = R*sqrt(u) keeps point density even (a plain r = R*u would
        // pile points near the centre). Angle is uniform on [0, 2π).
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = radius * Math.sqrt(random.nextDouble());
        int x = cx + (int) Math.round(Math.cos(angle) * distance);
        int z = cz + (int) Math.round(Math.sin(angle) * distance);

        Location candidateRegion = new Location(world, x, 0, z);
        world.getChunkAtAsync(candidateRegion)
                .thenCompose(chunk -> FoliaScheduler.global(plugin).supplyAtLocation(
                        candidateRegion, () -> toTopSafe(world, x, z)))
                .thenAccept(candidate -> {
                    if (candidate != null) {
                        callback.accept(candidate);
                    } else {
                        findSingleSpotAsync(world, spawn, radius, triesLeft - 1, callback);
                    }
                }).exceptionally(ex -> {
            callback.accept(spawn.clone().add(0.5, 1.0, 0.5));
            return null;
        });
    }

    private Location toTopSafe(World world, int x, int z) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            return toNetherSafe(world, x, z);
        }
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

    /**
     * Nether scatter spot. {@code getHighestBlockYAt} returns the bedrock ceiling here, so scan
     * downward from below the roof for the first solid, hazard-free floor with clear headroom.
     */
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
                .thenCompose(ignored -> FoliaScheduler.global(plugin).runEntityFuture(player, () -> {
                    player.setFallDistance(0f);
                    player.setFireTicks(0);
                }))
                .exceptionally(ignored -> null);
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
