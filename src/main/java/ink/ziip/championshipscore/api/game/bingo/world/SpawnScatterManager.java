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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Scatters players around a world's spawn into a ring, picking a safe top-of-surface spot per player
 * (clear of water/lava/hazards, with headroom). Ported from minebingo, with the {@code Tasks} wrapper
 * replaced by the Paper/Folia schedulers. Chunk loads use Paper's async chunk API; every safety scan then
 * runs on the region that owns the candidate chunk, and player reset work follows the entity.
 */
public final class SpawnScatterManager {
    private static final int MIN_RING_RADIUS = 8;
    private static final int MIN_PLAYER_DISTANCE_SQ = 24 * 24;
    private static final Set<Biome> WATER_BIOMES = Set.of(
            Biome.OCEAN, Biome.DEEP_OCEAN, Biome.WARM_OCEAN, Biome.LUKEWARM_OCEAN,
            Biome.DEEP_LUKEWARM_OCEAN, Biome.COLD_OCEAN, Biome.DEEP_COLD_OCEAN,
            Biome.FROZEN_OCEAN, Biome.DEEP_FROZEN_OCEAN, Biome.RIVER, Biome.FROZEN_RIVER
    );

    private final ChampionshipsCore plugin;

    public SpawnScatterManager(ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    public void performScatterAsync(World world, List<Player> players, int ringRadius, int jitter, int maxTries, Runnable onComplete) {
        if (world == null || players.isEmpty()) {
            if (onComplete != null) FoliaScheduler.global(plugin).runTask(onComplete);
            return;
        }
        int radius = Math.max(16, Math.max(MIN_RING_RADIUS, ringRadius));
        int effectiveJitter = Math.max(0, jitter);
        int tries = Math.max(8, maxTries);
        FoliaScheduler global = FoliaScheduler.global(plugin);
        global.supplyGlobal(world::getSpawnLocation).thenAccept(worldSpawn ->
            processPlayersAsync(world, worldSpawn, players, new ArrayList<>(), radius, effectiveJitter, tries, locations -> {
                List<CompletableFuture<Boolean>> teleports = new ArrayList<>();
                for (int i = 0; i < players.size(); i++) {
                    Location loc = i < locations.size() ? locations.get(i) : worldSpawn;
                    teleports.add(teleportReset(players.get(i), loc));
                }
                CompletableFuture.allOf(teleports.toArray(CompletableFuture[]::new)).thenRun(() -> {
                    if (onComplete != null) global.runTask(onComplete);
                });
            }))
        .exceptionally(throwable -> {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to start Bingo scatter", throwable);
            if (onComplete != null) global.runTask(onComplete);
            return null;
        });
    }

    private void processPlayersAsync(World world, Location worldSpawn, List<Player> players, List<Location> taken,
                                     int radius, int jitter, int tries, Consumer<List<Location>> onAllDone) {
        int index = taken.size();
        if (index >= players.size()) {
            onAllDone.accept(taken);
            return;
        }
        findSingleSpotAsync(world, worldSpawn, taken, radius, jitter, tries, loc -> {
            taken.add(loc);
            processPlayersAsync(world, worldSpawn, players, taken, radius, jitter, tries, onAllDone);
        });
    }

    private void findSingleSpotAsync(World world, Location worldSpawn, List<Location> taken,
                                     int radius, int jitter, int triesLeft, Consumer<Location> callback) {
        if (triesLeft <= 0) {
            FoliaScheduler.global(plugin).runAtLocation(worldSpawn,
                    () -> callback.accept(fallbackWorldSpawn(world, worldSpawn)));
            return;
        }
        int cx = worldSpawn.getBlockX();
        int cz = worldSpawn.getBlockZ();
        Random random = ThreadLocalRandom.current();
        double angle = random.nextDouble() * Math.PI * 2.0;
        int distance = radius + (jitter <= 0 ? 0 : random.nextInt(-jitter, jitter + 1));
        if (distance < MIN_RING_RADIUS) distance = MIN_RING_RADIUS;
        int x = cx + (int) Math.round(Math.cos(angle) * distance);
        int z = cz + (int) Math.round(Math.sin(angle) * distance);

        Location chunkLocation = new Location(world, x, 0, z);
        world.getChunkAtAsync(chunkLocation).thenAccept(chunk ->
                FoliaScheduler.global(plugin).runAtLocation(chunkLocation, () -> {
            Location candidate = toTopSafe(world, x, z);
            boolean valid = candidate != null;
            if (valid) {
                for (Location used : taken) {
                    if (used.distanceSquared(candidate) < MIN_PLAYER_DISTANCE_SQ) {
                        valid = false;
                        break;
                    }
                }
            }
            if (valid) {
                callback.accept(candidate);
            } else {
                findSingleSpotAsync(world, worldSpawn, taken, radius, jitter, triesLeft - 1, callback);
            }
        })).exceptionally(ex -> {
            FoliaScheduler.global(plugin).runAtLocation(worldSpawn,
                    () -> callback.accept(fallbackWorldSpawn(world, worldSpawn)));
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

    private CompletableFuture<Boolean> teleportReset(Player player, Location location) {
        return player.teleportAsync(location).thenApply(success -> {
            if (success) {
                FoliaScheduler.global(plugin).runEntity(player, () -> {
                    player.setFallDistance(0f);
                    player.setFireTicks(0);
                });
            }
            return success;
        }).exceptionally(ignored -> false);
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
