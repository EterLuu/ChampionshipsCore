package ink.ziip.championshipscore.api.game.buildmart.reference;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BlueprintBlock;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pastes and clears blueprint footprints in the world. The reference build is made of real blocks (so it
 * renders for everyone without packets) placed at the slot's reference anchor; player griefing is
 * prevented by the handler cancelling breaks inside reference footprints. The same footprint maths is
 * reused to wipe a completed/expired build back to air.
 */
public final class ReferenceBuilder {

    private ReferenceBuilder() {
    }

    /** Pastes every footprint chunk on the Folia region that owns that chunk. */
    public static CompletableFuture<Void> pasteAsync(ChampionshipsCore plugin,
                                                     BuildMartBlueprint blueprint, Location anchor) {
        return forEachChunk(plugin, blueprint, anchor, (world, baseX, baseY, baseZ, blocks) -> {
            for (BlueprintBlock block : blocks) {
                world.getBlockAt(baseX + block.getX(), baseY + block.getY(), baseZ + block.getZ())
                        .setBlockData(block.getBlockData(), false);
            }
        });
    }

    /** Clears every footprint chunk on the Folia region that owns that chunk. */
    public static CompletableFuture<Void> clearAsync(ChampionshipsCore plugin,
                                                     BuildMartBlueprint blueprint, Location anchor) {
        return forEachChunk(plugin, blueprint, anchor, (world, baseX, baseY, baseZ, blocks) -> {
            for (BlueprintBlock block : blocks) {
                world.getBlockAt(baseX + block.getX(), baseY + block.getY(), baseZ + block.getZ())
                        .setType(Material.AIR, false);
            }
        });
    }

    /** Counts a possibly cross-region footprint without reading any chunk from a foreign region. */
    public static CompletableFuture<Integer> countMatchingAsync(ChampionshipsCore plugin,
                                                                 BuildMartBlueprint blueprint,
                                                                 Location anchor) {
        World world = anchor.getWorld();
        if (world == null) return CompletableFuture.completedFuture(0);
        int baseX = anchor.getBlockX();
        int baseY = anchor.getBlockY();
        int baseZ = anchor.getBlockZ();
        FoliaScheduler scheduler = FoliaScheduler.global(plugin);
        AtomicInteger matched = new AtomicInteger();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (List<BlueprintBlock> blocks : byChunk(blueprint, anchor).values()) {
            BlueprintBlock first = blocks.getFirst();
            Location location = new Location(world, baseX + first.getX(), baseY + first.getY(), baseZ + first.getZ());
            futures.add(scheduler.runAtLocationFuture(location, () -> {
                int localMatched = 0;
                for (BlueprintBlock block : blocks) {
                    Block actual = world.getBlockAt(
                            baseX + block.getX(), baseY + block.getY(), baseZ + block.getZ());
                    if (actual.getBlockData().matches(block.getBlockData())) {
                        localMatched++;
                    }
                }
                matched.addAndGet(localMatched);
            }));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> matched.get());
    }

    /**
     * True when {@code worldX/Y/Z} is one of {@code blueprint}'s footprint cells anchored at
     * {@code anchor} — used to protect reference builds from being broken.
     */
    public static boolean isFootprintBlock(BuildMartBlueprint blueprint, Location anchor, int worldX, int worldY, int worldZ) {
        if (anchor.getWorld() == null) return false;
        int dx = worldX - anchor.getBlockX();
        int dy = worldY - anchor.getBlockY();
        int dz = worldZ - anchor.getBlockZ();
        for (BlueprintBlock b : blueprint.getBlocks()) {
            if (b.getX() == dx && b.getY() == dy && b.getZ() == dz) return true;
        }
        return false;
    }

    private static CompletableFuture<Void> forEachChunk(ChampionshipsCore plugin,
                                                        BuildMartBlueprint blueprint,
                                                        Location anchor,
                                                        ChunkAction action) {
        World world = anchor.getWorld();
        if (world == null) return CompletableFuture.completedFuture(null);
        int baseX = anchor.getBlockX();
        int baseY = anchor.getBlockY();
        int baseZ = anchor.getBlockZ();
        FoliaScheduler scheduler = FoliaScheduler.global(plugin);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (List<BlueprintBlock> blocks : byChunk(blueprint, anchor).values()) {
            BlueprintBlock first = blocks.getFirst();
            Location location = new Location(world, baseX + first.getX(), baseY + first.getY(), baseZ + first.getZ());
            futures.add(scheduler.runAtLocationFuture(location,
                    () -> action.accept(world, baseX, baseY, baseZ, blocks)));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private static Map<ChunkPosition, List<BlueprintBlock>> byChunk(BuildMartBlueprint blueprint,
                                                                    Location anchor) {
        int baseX = anchor.getBlockX();
        int baseZ = anchor.getBlockZ();
        Map<ChunkPosition, List<BlueprintBlock>> blocksByChunk = new HashMap<>();
        for (BlueprintBlock block : blueprint.getBlocks()) {
            int x = baseX + block.getX();
            int z = baseZ + block.getZ();
            blocksByChunk.computeIfAbsent(new ChunkPosition(x >> 4, z >> 4), ignored -> new ArrayList<>())
                    .add(block);
        }
        return blocksByChunk;
    }

    @FunctionalInterface
    private interface ChunkAction {
        void accept(World world, int baseX, int baseY, int baseZ, List<BlueprintBlock> blocks);
    }

    private record ChunkPosition(int x, int z) {
    }
}
